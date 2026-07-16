package com.mju.mjuton.recruitment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RecruitmentIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ChatService chatService;

	@Test
	void createRecruitmentMakesAuthorAChatMember() throws Exception {
		User author = user("rc-author@mju.ac.kr");
		long[] rec = createRecruitment(session(author), 4);
		long chatRoomId = rec[1];
		assertThat(chatService.isMember(chatRoomId, author.getId())).isTrue();
		assertThat(chatService.countMembers(chatRoomId)).isEqualTo(1);
	}

	@Test
	void applyThenApprovePromotesApplicantToChatMember() throws Exception {
		User author = user("flow-author@mju.ac.kr");
		User bob = user("flow-bob@mju.ac.kr");
		long[] rec = createRecruitment(session(author), 4);
		long recId = rec[0], chatRoomId = rec[1];

		long requestId = applyAs(session(bob), recId);
		assertThat(chatService.isMember(chatRoomId, bob.getId())).isFalse(); // 승인 전엔 멤버 아님

		mvc.perform(post("/api/recruitments/" + recId + "/applications/" + requestId + "/approve")
				.session(session(author)))
				.andExpect(status().isNoContent());
		assertThat(chatService.isMember(chatRoomId, bob.getId())).isTrue(); // 승인 후 채팅 멤버로 승격
	}

	@Test
	void authorCannotApplyAndDuplicatePendingRejected() throws Exception {
		User author = user("dup-author@mju.ac.kr");
		User bob = user("dup-bob@mju.ac.kr");
		long recId = createRecruitment(session(author), 4)[0];

		mvc.perform(post("/api/recruitments/" + recId + "/applications").session(session(author)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));

		mvc.perform(post("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
	}

	@Test
	void reapplyAfterRejectionIsAllowed() throws Exception {
		User author = user("re-author@mju.ac.kr");
		User bob = user("re-bob@mju.ac.kr");
		long recId = createRecruitment(session(author), 4)[0];

		long firstRequest = applyAs(session(bob), recId);
		mvc.perform(post("/api/recruitments/" + recId + "/applications/" + firstRequest + "/reject")
				.session(session(author)))
				.andExpect(status().isNoContent());
		// 거절 후 재신청은 자유롭게 허용된다.
		mvc.perform(post("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isCreated());
	}

	@Test
	void capacityFullBlocksApproval() throws Exception {
		User author = user("cap-author@mju.ac.kr");
		User bob = user("cap-bob@mju.ac.kr");
		User carol = user("cap-carol@mju.ac.kr");
		long recId = createRecruitment(session(author), 2)[0]; // 정원 2 = 방장 + 1명

		long bobRequest = applyAs(session(bob), recId);
		long carolRequest = applyAs(session(carol), recId);
		mvc.perform(post("/api/recruitments/" + recId + "/applications/" + bobRequest + "/approve")
				.session(session(author)))
				.andExpect(status().isNoContent()); // 여기서 정원(2) 도달

		mvc.perform(post("/api/recruitments/" + recId + "/applications/" + carolRequest + "/approve")
				.session(session(author)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CAPACITY_FULL"));
	}

	@Test
	void nonAuthorCannotManageAndClosedRejectsApplication() throws Exception {
		User author = user("mgmt-author@mju.ac.kr");
		User bob = user("mgmt-bob@mju.ac.kr");
		long recId = createRecruitment(session(author), 4)[0];

		// 방장이 아니면 신청 목록 조회 불가.
		mvc.perform(get("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_RECRUITMENT_AUTHOR"));

		// 방장이 마감하면 신청이 거부된다.
		mvc.perform(post("/api/recruitments/" + recId + "/close").session(session(author)))
				.andExpect(status().isNoContent());
		mvc.perform(post("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RECRUITMENT_CLOSED"));
	}

	@Test
	void transferOwnershipMovesAuthorityThenExOwnerCanLeave() throws Exception {
		User author = user("tr-author@mju.ac.kr");
		User bob = user("tr-bob@mju.ac.kr");
		long[] rec = createRecruitment(session(author), 4);
		long recId = rec[0], chatRoomId = rec[1];
		approve(session(author), recId, applyAs(session(bob), recId)); // bob을 멤버로

		// alice -> bob 방장 양도
		mvc.perform(post("/api/recruitments/" + recId + "/transfer").session(session(author))
				.contentType(MediaType.APPLICATION_JSON).content("{\"newOwnerId\":" + bob.getId() + "}"))
				.andExpect(status().isNoContent());

		// 권한이 넘어감: alice는 더 이상 방장 권한 없음, bob은 방장
		mvc.perform(get("/api/recruitments/" + recId + "/applications").session(session(author)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_RECRUITMENT_AUTHOR"));
		mvc.perform(get("/api/recruitments/" + recId + "/applications").session(session(bob)))
				.andExpect(status().isOk());

		// 양도한 alice(이제 일반 멤버)는 나갈 수 있다
		assertThat(chatService.isMember(chatRoomId, author.getId())).isTrue();
		mvc.perform(post("/api/recruitments/" + recId + "/leave").session(session(author)))
				.andExpect(status().isNoContent());
		assertThat(chatService.isMember(chatRoomId, author.getId())).isFalse();
	}

	@Test
	void ownerCannotLeaveWithoutTransferButMemberCan() throws Exception {
		User author = user("lv-author@mju.ac.kr");
		User bob = user("lv-bob@mju.ac.kr");
		long[] rec = createRecruitment(session(author), 4);
		long recId = rec[0], chatRoomId = rec[1];
		approve(session(author), recId, applyAs(session(bob), recId));

		// 방장은 양도 전엔 나갈 수 없다
		mvc.perform(post("/api/recruitments/" + recId + "/leave").session(session(author)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER_FIRST"));
		assertThat(chatService.isMember(chatRoomId, author.getId())).isTrue();

		// 일반 멤버는 그냥 나갈 수 있다
		mvc.perform(post("/api/recruitments/" + recId + "/leave").session(session(bob)))
				.andExpect(status().isNoContent());
		assertThat(chatService.isMember(chatRoomId, bob.getId())).isFalse();
	}

	@Test
	void cannotTransferToNonMember() throws Exception {
		User author = user("nm-author@mju.ac.kr");
		User carol = user("nm-carol@mju.ac.kr"); // 채팅방 멤버가 아님
		long recId = createRecruitment(session(author), 4)[0];
		mvc.perform(post("/api/recruitments/" + recId + "/transfer").session(session(author))
				.contentType(MediaType.APPLICATION_JSON).content("{\"newOwnerId\":" + carol.getId() + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("NEW_OWNER_NOT_MEMBER"));
	}

	@Test
	void nonAuthorCannotTransfer() throws Exception {
		User author = user("na-author@mju.ac.kr");
		User bob = user("na-bob@mju.ac.kr");
		long recId = createRecruitment(session(author), 4)[0];
		approve(session(author), recId, applyAs(session(bob), recId)); // bob은 멤버지만 방장 아님
		mvc.perform(post("/api/recruitments/" + recId + "/transfer").session(session(bob))
				.contentType(MediaType.APPLICATION_JSON).content("{\"newOwnerId\":" + bob.getId() + "}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_RECRUITMENT_AUTHOR"));
	}

	private void approve(MockHttpSession authorSession, long recruitmentId, long requestId) throws Exception {
		mvc.perform(post("/api/recruitments/" + recruitmentId + "/applications/" + requestId + "/approve")
				.session(authorSession)).andExpect(status().isNoContent());
	}

	/** POST /api/recruitments 로 모집글을 만들고 [recruitmentId, chatRoomId]를 돌려준다. */
	private long[] createRecruitment(MockHttpSession session, int capacity) throws Exception {
		String body = mvc.perform(post("/api/recruitments").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"백엔드 팀원 모집\",\"description\":\"스프링 함께 하실 분\",\"capacity\":" + capacity + "}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return new long[] { num(body, "$.id"), num(body, "$.chatRoomId") };
	}

	private long applyAs(MockHttpSession session, long recruitmentId) throws Exception {
		String body = mvc.perform(post("/api/recruitments/" + recruitmentId + "/applications").session(session))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return num(body, "$.id");
	}

	private static long num(String json, String path) {
		return ((Number) JsonPath.read(json, path)).longValue();
	}

	private MockHttpSession session(User user) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private User user(String email) {
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}
}
