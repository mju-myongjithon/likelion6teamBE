package com.mju.mjuton.event;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.repository.TagRepository;
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
class EventIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired EventRepository events;
	@Autowired StudyGroupRepository groups;
	@Autowired TagRepository tags;

	@BeforeEach
	void clearEvents() {
		groups.deleteAll();
		events.deleteAll();
	}

	@Test
	void creatorCanCreateReadUpdateAndDeleteEvent() throws Exception {
		MockHttpSession creator = sessionFor("event-creator@mju.ac.kr");
		long creatorId = (Long) creator.getAttribute(AuthController.SESSION_USER_ID);
		long eventId = create(creator, request("  CampusLink 해커톤  ",
				"2026-08-01T00:00:00Z", "2026-08-08T00:00:00Z", "2026-08-09T00:00:00Z",
				"\"posterUrl\":\" /event-posters/test.webp \",",
				"[\"  해커톤  \",\"개발\"]"));

		mvc.perform(get("/api/events/{eventId}", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.creatorUserId").value(creatorId))
				.andExpect(jsonPath("$.title").value("CampusLink 해커톤"))
				.andExpect(jsonPath("$.category").value("HACKATHON"))
				.andExpect(jsonPath("$.organizer").value("CampusLink"))
				.andExpect(jsonPath("$.relatedUrl").value("https://example.com/events/test"))
				.andExpect(jsonPath("$.posterUrl").value("/event-posters/test.webp"))
				.andExpect(jsonPath("$.tags[0]").value("해커톤"))
				.andExpect(jsonPath("$.tags[1]").value("개발"))
				.andExpect(jsonPath("$.members").doesNotExist())
				.andExpect(jsonPath("$.recruitingRoles").doesNotExist());

		mvc.perform(put("/api/events/{eventId}", eventId).session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(request("수정 행사", "2026-09-01T00:00:00Z", "2026-09-01T00:00:00Z",
						"2026-09-02T00:00:00Z", "[]")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("수정 행사"))
				.andExpect(jsonPath("$.tags").isEmpty());

		mvc.perform(delete("/api/events/{eventId}", eventId).session(creator))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/events/{eventId}", eventId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		assertTrue(tags.findByTypeAndName(TagType.EVENT, "해커톤").isPresent());
	}

	@Test
	void listIsPublicMinimalSummaryOrderedByNewestEvent() throws Exception {
		MockHttpSession creator = sessionFor("event-list@mju.ac.kr");
		long firstId = create(creator, request("첫 행사", "2026-08-01T00:00:00Z",
				"2026-08-02T00:00:00Z", "2026-08-03T00:00:00Z", "[]"));
		long secondId = create(creator, request("두 번째 행사", "2026-09-01T00:00:00Z",
				"2026-09-02T00:00:00Z", "2026-09-03T00:00:00Z",
				"\"posterUrl\":\"https://cdn.example.com/posters/list.jpg\",", "[]"));

		mvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eventId").value(secondId))
				.andExpect(jsonPath("$[0].category").value("HACKATHON"))
				.andExpect(jsonPath("$[0].applicationDeadlineAt").exists())
				.andExpect(jsonPath("$[0].startsAt").exists())
				.andExpect(jsonPath("$[0].location").value("서울"))
				.andExpect(jsonPath("$[0].posterUrl").value("https://cdn.example.com/posters/list.jpg"))
				.andExpect(jsonPath("$[0].description").doesNotExist())
				.andExpect(jsonPath("$[0].creatorUserId").doesNotExist())
				.andExpect(jsonPath("$[0].tags").doesNotExist())
				.andExpect(jsonPath("$[1].eventId").value(firstId));
	}

	@Test
	void eventCanListLinkedGroupsCreatedFromSelectedEvent() throws Exception {
		MockHttpSession creator = sessionFor("event-groups@mju.ac.kr");
		long eventId = create(creator, validRequest());

		MvcResult result = mvc.perform(post("/api/groups").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(groupRequest(eventId, "해커톤 백엔드 팀")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.eventId").value(eventId))
				.andExpect(jsonPath("$.eventTitle").value("테스트 행사"))
				.andReturn();
		Number groupId = JsonPath.read(result.getResponse().getContentAsString(), "$.groupId");

		mvc.perform(get("/api/events/{eventId}/groups", eventId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(groupId.longValue()))
				.andExpect(jsonPath("$[0].eventId").value(eventId))
				.andExpect(jsonPath("$[0].eventTitle").value("테스트 행사"))
				.andExpect(jsonPath("$[0].title").value("해커톤 백엔드 팀"));
	}

	@Test
	void writesRequireSessionAndOnlyCreatorCanChangeEvent() throws Exception {
		MockHttpSession creator = sessionFor("event-owner@mju.ac.kr");
		MockHttpSession other = sessionFor("event-other@mju.ac.kr");
		long eventId = create(creator, validRequest());

		mvc.perform(post("/api/events").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(put("/api/events/{eventId}", eventId).session(other).contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EVENT_FORBIDDEN"));
		mvc.perform(delete("/api/events/{eventId}", eventId).session(other))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EVENT_FORBIDDEN"));
	}

	@Test
	void validatesTimeUrlAndNormalizedCaseSensitiveTagDuplicates() throws Exception {
		MockHttpSession creator = sessionFor("event-validation@mju.ac.kr");
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(request("시간 오류", "2026-08-03T00:00:00Z", "2026-08-02T00:00:00Z",
						"2026-08-04T00:00:00Z", "[]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("https://example.com/events/test", "ftp://example.com/test")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"tags\":[]",
						"\"posterUrl\":\"data:image/png;base64,test\",\"tags\":[]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		for (String invalidPosterUrl : new String[] {
				"//cdn.example.com/poster.webp",
				"/event-posters/../secret.webp",
				"/event-posters/%2e%2e/secret.webp"
		}) {
			mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
					.content(validRequest().replace("\"tags\":[]",
							"\"posterUrl\":\"" + invalidPosterUrl + "\",\"tags\":[]")))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(request("중복", "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z",
						"2026-08-03T00:00:00Z", "[\"AI\",\" AI \"]")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(request("대소문자", "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z",
						"2026-08-03T00:00:00Z", "[\"AI\",\"ai\"]")))
				.andExpect(status().isCreated());
	}

	@Test
	void tagsAreRequiredAndMalformedJsonUsesCommonError() throws Exception {
		MockHttpSession creator = sessionFor("event-malformed@mju.ac.kr");
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace(",\"tags\":[]", "")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mvc.perform(post("/api/events").session(creator).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void updateCanKeepAnExistingTagWhileReplacingTheOthers() throws Exception {
		MockHttpSession creator = sessionFor("event-retained-tag@mju.ac.kr");
		long eventId = create(creator, request("태그 수정 행사", "2026-08-01T00:00:00Z",
				"2026-08-02T00:00:00Z", "2026-08-03T00:00:00Z", "[\"AI\",\"백엔드\"]"));

		mvc.perform(put("/api/events/{eventId}", eventId).session(creator).contentType(MediaType.APPLICATION_JSON)
				.content(request("태그 수정 행사", "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z",
						"2026-08-03T00:00:00Z", "[\"AI\",\"프론트엔드\"]")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tags[0]").value("AI"))
				.andExpect(jsonPath("$.tags[1]").value("프론트엔드"));
	}

	private long create(MockHttpSession session, String content) throws Exception {
		MvcResult result = mvc.perform(post("/api/events").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(content)).andExpect(status().isCreated()).andReturn();
		Number eventId = JsonPath.read(result.getResponse().getContentAsString(), "$.eventId");
		return eventId.longValue();
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.findByEmail(email).orElseGet(() ->
				users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123"))));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private String validRequest() {
		return request("테스트 행사", "2026-08-01T00:00:00Z", "2026-08-02T00:00:00Z",
				"2026-08-03T00:00:00Z", "[]");
	}

	private String request(String title, String deadline, String startsAt, String endsAt, String tags) {
		return request(title, deadline, startsAt, endsAt, "", tags);
	}

	private String request(String title, String deadline, String startsAt, String endsAt, String optionalFields,
			String tags) {
		return "{\"title\":\"" + title + "\",\"description\":\"행사 정보입니다.\","
				+ "\"organizer\":\"CampusLink\",\"applicationDeadlineAt\":\"" + deadline + "\","
				+ "\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\","
				+ "\"location\":\"서울\",\"relatedUrl\":\"https://example.com/events/test\"," + optionalFields
				+ "\"tags\":"
				+ tags + "}";
	}

	private String groupRequest(long eventId, String title) {
		return "{\"eventId\":" + eventId + ",\"title\":\"" + title + "\",\"description\":\"팀원을 모집합니다.\","
				+ "\"maxMemberCount\":4,\"meetingRule\":\"온라인 회의\",\"location\":\"명지대\","
				+ "\"recruitingRoles\":[{\"role\":\"백엔드\",\"skill\":\"Spring\"}]}";
	}
}
