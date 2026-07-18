package com.mju.mjuton.listing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import java.sql.Timestamp;
import java.time.Instant;
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

@SpringBootTest
@AutoConfigureMockMvc
class ListingIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired EventRepository events;
	@Autowired StudyGroupRepository groups;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void clearListings() {
		events.deleteAll();
		groups.deleteAll();
	}

	@Test
	void omittedFilterAndAllReturnBothTypesInTheFixedOrder() throws Exception {
		MockHttpSession session = sessionFor("listing-all@mju.ac.kr");
		long firstGroupId = createGroup(session, "첫 스터디");
		long secondGroupId = createGroup(session, "둘째 스터디");
		long olderGroupId = createGroup(session, "오래된 스터디");
		long eventId = createEvent(session, "통합 해커톤");
		Instant latestTime = Instant.parse("2026-07-17T00:00:00Z");
		Instant olderTime = Instant.parse("2026-07-16T00:00:00Z");
		jdbc.update("update groups set created_at = ? where group_id in (?, ?)",
				Timestamp.from(latestTime), firstGroupId, secondGroupId);
		jdbc.update("update groups set created_at = ? where group_id = ?", Timestamp.from(olderTime), olderGroupId);
		jdbc.update("update events set created_at = ? where event_id = ?", Timestamp.from(latestTime), eventId);

		mvc.perform(get("/api/listings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].category").value("HACKATHON"))
				.andExpect(jsonPath("$[0].eventId").value(eventId))
				.andExpect(jsonPath("$[0].groupId").doesNotExist())
				.andExpect(jsonPath("$[0].meetingRule").doesNotExist())
				.andExpect(jsonPath("$[0].id").doesNotExist())
				.andExpect(jsonPath("$[0].description").doesNotExist())
				.andExpect(jsonPath("$[0].organizer").doesNotExist())
				.andExpect(jsonPath("$[0].endsAt").doesNotExist())
				.andExpect(jsonPath("$[0].relatedUrl").doesNotExist())
				.andExpect(jsonPath("$[0].posterUrl").value("https://cdn.example.com/posters/listing.jpg"))
				.andExpect(jsonPath("$[0].tags").doesNotExist())
				.andExpect(jsonPath("$[0].currentMemberCount").doesNotExist())
				.andExpect(jsonPath("$[0].matchPercentage").doesNotExist())
				.andExpect(jsonPath("$[0].scrapped").doesNotExist())
				.andExpect(jsonPath("$[1].category").value("STUDY"))
				.andExpect(jsonPath("$[1].groupId").value(secondGroupId))
				.andExpect(jsonPath("$[1].eventId").doesNotExist())
				.andExpect(jsonPath("$[1].startsAt").doesNotExist())
				.andExpect(jsonPath("$[1].applicationDeadlineAt").doesNotExist())
				.andExpect(jsonPath("$[1].id").doesNotExist())
				.andExpect(jsonPath("$[1].description").doesNotExist())
				.andExpect(jsonPath("$[1].leaderUserId").doesNotExist())
				.andExpect(jsonPath("$[1].recruitingRoles").doesNotExist())
				.andExpect(jsonPath("$[1].currentMemberCount").value(1))
				.andExpect(jsonPath("$[1].matchPercentage").doesNotExist())
				.andExpect(jsonPath("$[1].scrapped").doesNotExist())
				.andExpect(jsonPath("$[2].groupId").value(firstGroupId))
				.andExpect(jsonPath("$[3].groupId").value(olderGroupId));

		mvc.perform(get("/api/listings").param("filter", "ALL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].eventId").value(eventId))
				.andExpect(jsonPath("$[1].groupId").value(secondGroupId))
				.andExpect(jsonPath("$[2].groupId").value(firstGroupId))
				.andExpect(jsonPath("$[3].groupId").value(olderGroupId));
	}

	@Test
	void studyAndHackathonFiltersReturnOnlyTheirOwnTypeAndDetailIdsRemainUsable() throws Exception {
		MockHttpSession session = sessionFor("listing-filter@mju.ac.kr");
		long groupId = createGroup(session, "필터 스터디");
		long eventId = createEvent(session, "필터 해커톤");

		mvc.perform(get("/api/listings").param("filter", "STUDY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].category").value("STUDY"))
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].currentMemberCount").value(1))
				.andExpect(jsonPath("$[0].eventId").doesNotExist());
		mvc.perform(get("/api/listings").param("filter", "HACKATHON"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].category").value("HACKATHON"))
				.andExpect(jsonPath("$[0].eventId").value(eventId))
				.andExpect(jsonPath("$[0].currentMemberCount").doesNotExist())
				.andExpect(jsonPath("$[0].groupId").doesNotExist());
		mvc.perform(get("/api/groups/{groupId}", groupId)).andExpect(status().isOk());
		mvc.perform(get("/api/events/{eventId}", eventId)).andExpect(status().isOk());
	}

	@Test
	void emptyDatabaseReturnsAnEmptyPublicList() throws Exception {
		mvc.perform(get("/api/listings"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void emptyLowercaseWhitespaceAndUnknownFiltersUseTheCommonBadRequest() throws Exception {
		for (String filter : new String[] {"", "all", " ALL", "ALL ", "UNKNOWN"}) {
			mvc.perform(get("/api/listings").param("filter", filter))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
	}

	private long createGroup(MockHttpSession session, String title) throws Exception {
		String body = "{\"title\":\"" + title + "\",\"description\":\"모임 소개\","
				+ "\"maxMemberCount\":8,\"meetingRule\":\"매주 토요일\","
				+ "\"location\":\"서울\",\"recruitingRoles\":[]}";
		MvcResult result = mvc.perform(post("/api/groups").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.currentMemberCount").value(1))
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.groupId")).longValue();
	}

	private long createEvent(MockHttpSession session, String title) throws Exception {
		String body = "{\"title\":\"" + title + "\",\"description\":\"행사 소개\","
				+ "\"organizer\":\"CampusLink\",\"applicationDeadlineAt\":\"2026-08-01T00:00:00Z\","
				+ "\"startsAt\":\"2026-08-02T00:00:00Z\",\"endsAt\":\"2026-08-03T00:00:00Z\","
				+ "\"location\":\"서울\",\"relatedUrl\":\"https://example.com/listing-test\","
				+ "\"posterUrl\":\"https://cdn.example.com/posters/listing.jpg\",\"tags\":[]}";
		MvcResult result = mvc.perform(post("/api/events").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(body)).andExpect(status().isCreated()).andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.eventId")).longValue();
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.findByEmail(email).orElseGet(() ->
				users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123"))));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}
}
