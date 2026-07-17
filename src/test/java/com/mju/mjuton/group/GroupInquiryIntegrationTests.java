package com.mju.mjuton.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupInquiryRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.group.service.GroupInquiryService;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class GroupInquiryIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupInquiryRepository inquiries;
	@Autowired GroupInquiryService inquiryService;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactionManager;

	@BeforeEach
	void clearGroups() {
		inquiries.deleteAll();
		groups.deleteAll();
	}

	@Test
	void inquiriesArePublicAndExposePermissionsForCurrentUser() throws Exception {
		MockHttpSession leader = sessionFor("inquiry-flags-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-flags-author@mju.ac.kr");
		long groupId = createGroup(leader);
		long inquiryId = createInquiry(groupId, author, "  모임 장소가 어디인가요?  ");

		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].inquiryId").value(inquiryId))
				.andExpect(jsonPath("$.content[0].content").value("모임 장소가 어디인가요?"))
				.andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.content[0].answer").isEmpty())
				.andExpect(jsonPath("$.content[0].canDelete").value(false))
				.andExpect(jsonPath("$.content[0].canAnswer").value(false));
		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId).session(author))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].canDelete").value(true))
				.andExpect(jsonPath("$.content[0].canAnswer").value(false));
		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId).session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].canDelete").value(false))
				.andExpect(jsonPath("$.content[0].canAnswer").value(true));

		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, inquiryId)
						.session(leader).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"  학생회관 3층입니다.  \"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.answer.content").value("학생회관 3층입니다."))
				.andExpect(jsonPath("$.answer.answeredAt").isNotEmpty())
				.andExpect(jsonPath("$.canAnswer").value(false));
	}

	@Test
	void writeEndpointsRequireLoginAndValidateNormalizedContentAndPage() throws Exception {
		MockHttpSession leader = sessionFor("inquiry-validation-leader@mju.ac.kr");
		long groupId = createGroup(leader);

		mvc.perform(post("/api/groups/{groupId}/inquiries", groupId)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"문의\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, Long.MAX_VALUE)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"답변\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(post("/api/groups/{groupId}/inquiries", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, Long.MAX_VALUE)
						.session(leader).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"   \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/groups/{groupId}/inquiries", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + "가".repeat(1001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId).param("page", "-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId).param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void onlyAuthorCanDeleteAndOnlyLeaderCanAnswerOnce() throws Exception {
		MockHttpSession leader = sessionFor("inquiry-authority-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-authority-author@mju.ac.kr");
		MockHttpSession other = sessionFor("inquiry-authority-other@mju.ac.kr");
		long groupId = createGroup(leader);
		long inquiryId = createInquiry(groupId, author, "권한 문의");

		mvc.perform(delete("/api/groups/{groupId}/inquiries/{inquiryId}", groupId, inquiryId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(delete("/api/groups/{groupId}/inquiries/{inquiryId}", groupId, inquiryId).session(other))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INQUIRY_AUTHOR_REQUIRED"));
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, inquiryId)
						.session(other).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"답변\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, inquiryId)
						.session(leader).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"첫 답변\"}"))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", groupId, inquiryId)
						.session(leader).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"두 번째 답변\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INQUIRY_ALREADY_ANSWERED"));

		mvc.perform(delete("/api/groups/{groupId}/inquiries/{inquiryId}", groupId, inquiryId).session(author))
				.andExpect(status().isNoContent());
		assertThat(inquiries.existsById(inquiryId)).isFalse();
	}

	@Test
	void missingAndCrossGroupTargetsReturnNotFound() throws Exception {
		MockHttpSession firstLeader = sessionFor("inquiry-target-first-leader@mju.ac.kr");
		MockHttpSession secondLeader = sessionFor("inquiry-target-second-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-target-author@mju.ac.kr");
		long firstGroupId = createGroup(firstLeader);
		long secondGroupId = createGroup(secondLeader);
		long inquiryId = createInquiry(firstGroupId, author, "대상 문의");

		mvc.perform(get("/api/groups/{groupId}/inquiries", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(delete("/api/groups/{groupId}/inquiries/{inquiryId}", secondGroupId, inquiryId)
						.session(author))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_INQUIRY_NOT_FOUND"));
		mvc.perform(post("/api/groups/{groupId}/inquiries/{inquiryId}/answer", secondGroupId, inquiryId)
						.session(secondLeader).contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"잘못된 대상 답변\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_INQUIRY_NOT_FOUND"));
		mvc.perform(delete("/api/groups/{groupId}/inquiries/{inquiryId}", firstGroupId, Long.MAX_VALUE)
						.session(author))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_INQUIRY_NOT_FOUND"));
	}

	@Test
	void inquiryPagesUseLatestFirstStableOrdering() throws Exception {
		MockHttpSession leader = sessionFor("inquiry-page-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-page-author@mju.ac.kr");
		long groupId = createGroup(leader);
		for (int index = 1; index <= 7; index++) {
			createInquiry(groupId, author, "문의 " + index);
		}

		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(5))
				.andExpect(jsonPath("$.content[0].content").value("문의 7"))
				.andExpect(jsonPath("$.content[4].content").value("문의 3"))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(5))
				.andExpect(jsonPath("$.totalElements").value(7))
				.andExpect(jsonPath("$.totalPages").value(2))
				.andExpect(jsonPath("$.first").value(true))
				.andExpect(jsonPath("$.last").value(false));
		mvc.perform(get("/api/groups/{groupId}/inquiries", groupId).param("page", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].content").value("문의 2"))
				.andExpect(jsonPath("$.content[1].content").value("문의 1"))
				.andExpect(jsonPath("$.last").value(true));
	}

	@Test
	void concurrentAnswersAllowExactlyOneAndGroupDeletionUsesDatabaseCascade() throws Exception {
		MockHttpSession leader = sessionFor("inquiry-concurrent-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-concurrent-author@mju.ac.kr");
		long groupId = createGroup(leader);
		long inquiryId = createInquiry(groupId, author, "동시 답변 문의");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = executor.submit(
					() -> answerConcurrently(start, groupId, inquiryId, userId(leader), "첫 답변"));
			Future<String> second = executor.submit(
					() -> answerConcurrently(start, groupId, inquiryId, userId(leader), "둘째 답변"));
			start.countDown();
			assertThat(List.of(first.get(), second.get()))
					.containsExactlyInAnyOrder("ANSWERED", "INQUIRY_ALREADY_ANSWERED");
		} finally {
			executor.shutdownNow();
		}

		mvc.perform(delete("/api/groups/{groupId}", groupId).session(leader))
				.andExpect(status().isNoContent());
		assertThat(inquiries.existsById(inquiryId)).isFalse();

		User leaderUser = users.findById(userId(leader)).orElseThrow();
		StudyGroup legacyGroup = new StudyGroup(
				leaderUser, "DB cascade 문의 모임", "소개", 3, "매주", "서울");
		legacyGroup.replaceRoles(List.of());
		long legacyGroupId = groups.saveAndFlush(legacyGroup).getId();
		long legacyInquiryId = createInquiry(legacyGroupId, author, "DB cascade 문의");
		assertThat(jdbc.update("delete from groups where group_id = ?", legacyGroupId)).isEqualTo(1);
		assertThat(inquiries.existsById(legacyInquiryId)).isFalse();
	}

	@Test
	void answerWaitsForLeadershipTransferAndUsesCommittedLeader() throws Exception {
		MockHttpSession oldLeader = sessionFor("inquiry-transfer-old-leader@mju.ac.kr");
		MockHttpSession newLeader = sessionFor("inquiry-transfer-new-leader@mju.ac.kr");
		MockHttpSession author = sessionFor("inquiry-transfer-author@mju.ac.kr");
		long groupId = createGroup(oldLeader);
		long inquiryId = createInquiry(groupId, author, "리더 양도 경합 문의");
		User newLeaderUser = users.findById(userId(newLeader)).orElseThrow();
		CountDownLatch transferHasLock = new CountDownLatch(1);
		CountDownLatch allowTransferCommit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Void> transfer = executor.submit(() -> {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					StudyGroup group = groups.findByIdForUpdate(groupId).orElseThrow();
					group.transferLeadership(newLeaderUser);
					groups.flush();
					transferHasLock.countDown();
					try {
						allowTransferCommit.await();
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(exception);
					}
				});
				return null;
			});
			assertThat(transferHasLock.await(2, TimeUnit.SECONDS)).isTrue();

			Future<String> oldLeaderAnswer = executor.submit(
					() -> answerConcurrently(new CountDownLatch(0), groupId, inquiryId,
							userId(oldLeader), "이전 리더 답변"));
			assertThatThrownBy(() -> oldLeaderAnswer.get(200, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);

			allowTransferCommit.countDown();
			transfer.get();
			assertThat(oldLeaderAnswer.get()).isEqualTo("GROUP_LEADER_REQUIRED");
			assertThat(inquiryService.answer(groupId, inquiryId, userId(newLeader), "새 리더 답변")
					.answer().content()).isEqualTo("새 리더 답변");
		} finally {
			allowTransferCommit.countDown();
			executor.shutdownNow();
		}
	}

	private String answerConcurrently(CountDownLatch start, long groupId, long inquiryId,
			long leaderId, String content) throws InterruptedException {
		start.await();
		try {
			inquiryService.answer(groupId, inquiryId, leaderId, content);
			return "ANSWERED";
		} catch (ApiException exception) {
			return exception.getCode();
		}
	}

	private long createGroup(MockHttpSession leader) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups").session(leader)
						.contentType(MediaType.APPLICATION_JSON).content(groupRequest()))
				.andExpect(status().isCreated()).andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.groupId")).longValue();
	}

	private long createInquiry(long groupId, MockHttpSession author, String content) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups/{groupId}/inquiries", groupId).session(author)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"" + content + "\"}"))
				.andExpect(status().isCreated()).andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.inquiryId")).longValue();
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private long userId(MockHttpSession session) {
		return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
	}

	private String groupRequest() {
		return "{\"title\":\"문의 기능 스터디\",\"description\":\"함께 공부합니다.\","
				+ "\"maxMemberCount\":5,\"meetingRule\":\"매주 토요일\","
				+ "\"location\":\"강남\",\"recruitingRoles\":[]}";
	}
}
