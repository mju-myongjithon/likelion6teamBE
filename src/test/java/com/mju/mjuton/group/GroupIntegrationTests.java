package com.mju.mjuton.group;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GroupIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;

	@BeforeEach
	void clearGroups() {
		groups.deleteAll();
	}

	@Test
	void leaderCanCreateReadUpdateAndDeleteGroup() throws Exception {
		MockHttpSession leader = sessionFor("group-leader@mju.ac.kr");
		long leaderId = (Long) leader.getAttribute(AuthController.SESSION_USER_ID);
		long groupId = create(leader, request("  알고리즘 스터디  ", 8,
				"[{\"role\":\"  프론트엔드  \",\"skill\":\" React \"},{\"role\":\"백엔드\",\"skill\":null}]"));

		mvc.perform(get("/api/groups/{groupId}", groupId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.leaderUserId").value(leaderId))
				.andExpect(jsonPath("$.title").value("알고리즘 스터디"))
				.andExpect(jsonPath("$.category").value("STUDY"))
				.andExpect(jsonPath("$.maxMemberCount").value(8))
				.andExpect(jsonPath("$.recruitingRoles[0].role").value("프론트엔드"))
				.andExpect(jsonPath("$.recruitingRoles[0].skill").value("React"))
				.andExpect(jsonPath("$.recruitingRoles[1].role").value("백엔드"))
				.andExpect(jsonPath("$.recruitingRoles[1].skill").isEmpty());

		mvc.perform(put("/api/groups/{groupId}", groupId).session(leader).contentType(MediaType.APPLICATION_JSON)
				.content(request("수정된 스터디", 10, "[]")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("수정된 스터디"))
				.andExpect(jsonPath("$.maxMemberCount").value(10))
				.andExpect(jsonPath("$.recruitingRoles").isEmpty());

		mvc.perform(delete("/api/groups/{groupId}", groupId).session(leader))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/groups/{groupId}", groupId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
	}

	@Test
	void listIsPublicSummaryOrderedByNewestGroup() throws Exception {
		MockHttpSession leader = sessionFor("group-list@mju.ac.kr");
		long firstId = create(leader, request("첫 번째", 3, "[]"));
		long secondId = create(leader, request("두 번째", 4, "[]"));

		mvc.perform(get("/api/groups"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(secondId))
				.andExpect(jsonPath("$[0].title").value("두 번째"))
				.andExpect(jsonPath("$[0].category").value("STUDY"))
				.andExpect(jsonPath("$[0].description").doesNotExist())
				.andExpect(jsonPath("$[0].leaderUserId").doesNotExist())
				.andExpect(jsonPath("$[1].groupId").value(firstId));
	}

	@Test
	void writesRequireSessionAndOnlyLeaderCanChangeGroup() throws Exception {
		MockHttpSession leader = sessionFor("group-owner@mju.ac.kr");
		MockHttpSession other = sessionFor("group-other@mju.ac.kr");
		long groupId = create(leader, request("소유권 테스트", 5, "[]"));

		mvc.perform(post("/api/groups").contentType(MediaType.APPLICATION_JSON)
				.content(request("비로그인", 5, "[]")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(put("/api/groups/{groupId}", groupId).session(other).contentType(MediaType.APPLICATION_JSON)
				.content(request("권한 없음", 5, "[]")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_FORBIDDEN"));
		mvc.perform(delete("/api/groups/{groupId}", groupId).session(other))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_FORBIDDEN"));
	}

	@Test
	void validatesFieldsRolesAndNormalizedCaseSensitiveDuplicates() throws Exception {
		MockHttpSession leader = sessionFor("group-validation@mju.ac.kr");
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content(request("   ", 1, "[]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content(request("정원 오류", 101, "[]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content(request("중복 오류", 5,
						"[{\"role\":\"개발\",\"skill\":\"Java\"},{\"role\":\" 개발 \",\"skill\":\" Java \"}]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content(request("대소문자 허용", 5,
						"[{\"role\":\"개발\",\"skill\":\"Java\"},{\"role\":\"개발\",\"skill\":\"java\"}]")))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"역할 누락\",\"description\":\"소개\",\"maxMemberCount\":5,"
						+ "\"meetingRule\":\"규칙\",\"location\":\"서울\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void malformedJsonUsesCommonErrorResponse() throws Exception {
		MockHttpSession leader = sessionFor("group-malformed@mju.ac.kr");
		mvc.perform(post("/api/groups").session(leader).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	private long create(MockHttpSession session, String content) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(content)).andExpect(status().isCreated()).andReturn();
		Number groupId = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.groupId");
		return groupId.longValue();
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private String request(String title, int maxMemberCount, String roles) {
		return "{\"title\":\"" + title + "\",\"description\":\"코딩 테스트를 준비합니다.\","
				+ "\"maxMemberCount\":" + maxMemberCount + ",\"meetingRule\":\"매주 토요일\","
				+ "\"location\":\"강남\",\"recruitingRoles\":" + roles + "}";
	}
}
