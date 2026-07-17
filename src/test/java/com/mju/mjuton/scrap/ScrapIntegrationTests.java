package com.mju.mjuton.scrap;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.scrap.repository.EventScrapRepository;
import com.mju.mjuton.scrap.repository.GroupScrapRepository;
import com.mju.mjuton.scrap.service.ScrapService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
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
class ScrapIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ProfileRepository profiles;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository members;
	@Autowired EventRepository events;
	@Autowired GroupScrapRepository groupScraps;
	@Autowired EventScrapRepository eventScraps;
	@Autowired ScrapService scraps;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void clearTargets() {
		groupScraps.deleteAll();
		eventScraps.deleteAll();
		events.deleteAll();
		groups.deleteAll();
	}

	@Test
	void savesBothTypesIdempotentlyAndReturnsUnifiedStableSummary() throws Exception {
		MockHttpSession owner = sessionFor("scrap-summary-owner@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-summary-user@mju.ac.kr");
		saveProfile(owner, "저장 모임 리더", "https://example.com/scrap-leader.png");
		long groupId = createGroup(owner, "저장한 스터디", 8);
		long eventId = createEvent(owner, "저장한 해커톤");

		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(user))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(user))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/scraps/events/{eventId}", eventId).session(user))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/scraps/events/{eventId}", eventId).session(user))
				.andExpect(status().isNoContent());
		assertThat(groupScraps.count()).isEqualTo(1);
		assertThat(eventScraps.count()).isEqualTo(1);
		Timestamp sameTime = Timestamp.from(Instant.parse("2026-07-17T00:00:00Z"));
		jdbc.update("update group_scraps set created_at = ?", sameTime);
		jdbc.update("update event_scraps set created_at = ?", sameTime);

		mvc.perform(get("/api/scraps/me").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].category").value("HACKATHON"))
				.andExpect(jsonPath("$[0].eventId").value(eventId))
				.andExpect(jsonPath("$[0].title").value("저장한 해커톤"))
				.andExpect(jsonPath("$[0].organizer").value("CampusLink"))
				.andExpect(jsonPath("$[0].applicationDeadlineAt").isNotEmpty())
				.andExpect(jsonPath("$[0].startsAt").isNotEmpty())
				.andExpect(jsonPath("$[0].endsAt").isNotEmpty())
				.andExpect(jsonPath("$[0].location").value("서울"))
				.andExpect(jsonPath("$[0].scrappedAt").isNotEmpty())
				.andExpect(jsonPath("$[0].groupId").doesNotExist())
				.andExpect(jsonPath("$[1].category").value("STUDY"))
				.andExpect(jsonPath("$[1].groupId").value(groupId))
				.andExpect(jsonPath("$[1].title").value("저장한 스터디"))
				.andExpect(jsonPath("$[1].leaderUserId").value(userId(owner)))
				.andExpect(jsonPath("$[1].leaderName").value("저장 모임 리더"))
				.andExpect(jsonPath("$[1].leaderAvatarUrl")
						.value("https://example.com/scrap-leader.png"))
				.andExpect(jsonPath("$[1].meetingRule").value("매주 토요일"))
				.andExpect(jsonPath("$[1].location").value("서울"))
				.andExpect(jsonPath("$[1].currentMemberCount").value(1))
				.andExpect(jsonPath("$[1].maxMemberCount").value(8))
				.andExpect(jsonPath("$[1].eventId").doesNotExist());
	}

	@Test
	void scrapsAreIsolatedByUserAndRemovalIsIdempotent() throws Exception {
		MockHttpSession owner = sessionFor("scrap-isolation-owner@mju.ac.kr");
		MockHttpSession first = sessionFor("scrap-isolation-first@mju.ac.kr");
		MockHttpSession second = sessionFor("scrap-isolation-second@mju.ac.kr");
		long groupId = createGroup(owner, "사용자별 저장", 4);

		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(first))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(second))
				.andExpect(status().isNoContent());
		assertThat(groupScraps.count()).isEqualTo(2);

		mvc.perform(delete("/api/scraps/groups/{groupId}", groupId).session(first))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/scraps/groups/{groupId}", groupId).session(first))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/scraps/groups/{groupId}", Long.MAX_VALUE).session(first))
				.andExpect(status().isNoContent());
		assertThat(groupScraps.existsByUser_IdAndGroup_Id(userId(first), groupId)).isFalse();
		assertThat(groupScraps.existsByUser_IdAndGroup_Id(userId(second), groupId)).isTrue();
		mvc.perform(get("/api/scraps/me").session(first))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
		mvc.perform(get("/api/scraps/me").session(second))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void concurrentRepeatedSavesCreateOneRowPerTarget() throws Exception {
		MockHttpSession owner = sessionFor("scrap-concurrent-owner@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-concurrent-user@mju.ac.kr");
		long groupId = createGroup(owner, "동시 저장 스터디", 4);
		long eventId = createEvent(owner, "동시 저장 해커톤");
		runConcurrently(8, () -> scraps.saveGroup(userId(user), groupId));
		runConcurrently(8, () -> scraps.saveEvent(userId(user), eventId));

		assertThat(groupScraps.count()).isEqualTo(1);
		assertThat(eventScraps.count()).isEqualTo(1);
	}

	@Test
	void concurrentMixedSaveAndRemoveRemainIdempotentPerTarget() throws Exception {
		MockHttpSession owner = sessionFor("scrap-mixed-owner@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-mixed-user@mju.ac.kr");
		long groupId = createGroup(owner, "혼합 저장 스터디", 4);
		long eventId = createEvent(owner, "혼합 저장 해커톤");

		AtomicInteger groupOperation = new AtomicInteger();
		runConcurrently(12, () -> {
			if (groupOperation.getAndIncrement() % 2 == 0) {
				mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(user))
						.andExpect(status().isNoContent());
			} else {
				mvc.perform(delete("/api/scraps/groups/{groupId}", groupId).session(user))
						.andExpect(status().isNoContent());
			}
		});

		AtomicInteger eventOperation = new AtomicInteger();
		runConcurrently(12, () -> {
			if (eventOperation.getAndIncrement() % 2 == 0) {
				mvc.perform(put("/api/scraps/events/{eventId}", eventId).session(user))
						.andExpect(status().isNoContent());
			} else {
				mvc.perform(delete("/api/scraps/events/{eventId}", eventId).session(user))
						.andExpect(status().isNoContent());
			}
		});

		Long groupRows = jdbc.queryForObject(
				"select count(*) from group_scraps where user_id = ? and group_id = ?",
				Long.class, userId(user), groupId);
		Long eventRows = jdbc.queryForObject(
				"select count(*) from event_scraps where user_id = ? and event_id = ?",
				Long.class, userId(user), eventId);
		assertThat(groupRows).isBetween(0L, 1L);
		assertThat(eventRows).isBetween(0L, 1L);
	}

	@Test
	void targetDeletionCascadesScraps() throws Exception {
		MockHttpSession owner = sessionFor("scrap-cascade-owner@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-cascade-user@mju.ac.kr");
		long groupId = createGroup(owner, "삭제될 저장 모임", 4);
		long eventId = createEvent(owner, "삭제될 저장 행사");
		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(user))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/scraps/events/{eventId}", eventId).session(user))
				.andExpect(status().isNoContent());

		mvc.perform(delete("/api/groups/{groupId}", groupId).session(owner))
				.andExpect(status().isNoContent());
		mvc.perform(delete("/api/events/{eventId}", eventId).session(owner))
				.andExpect(status().isNoContent());
		assertThat(groupScraps.count()).isZero();
		assertThat(eventScraps.count()).isZero();
		mvc.perform(get("/api/scraps/me").session(user))
				.andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void directSqlTargetDeletionUsesDatabaseCascade() {
		MockHttpSession owner = sessionFor("scrap-sql-cascade-owner@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-sql-cascade-user@mju.ac.kr");
		User ownerUser = users.findById(userId(owner)).orElseThrow();
		StudyGroup group = new StudyGroup(ownerUser, "SQL 삭제 모임", "소개", 4, "매주", "서울");
		group.replaceRoles(List.of());
		long groupId = groups.saveAndFlush(group).getId();
		Instant startsAt = Instant.now().plusSeconds(7200);
		Event event = events.saveAndFlush(new Event(ownerUser, "SQL 삭제 행사", "소개", "CampusLink",
				Instant.now().plusSeconds(3600), startsAt, startsAt.plusSeconds(3600),
				"서울", "https://example.com/sql-cascade"));
		long eventId = event.getId();
		scraps.saveGroup(userId(user), groupId);
		scraps.saveEvent(userId(user), eventId);

		assertThat(jdbc.update("delete from groups where group_id = ?", groupId)).isEqualTo(1);
		assertThat(jdbc.update("delete from events where event_id = ?", eventId)).isEqualTo(1);

		assertThat(groupScraps.existsByUser_IdAndGroup_Id(userId(user), groupId)).isFalse();
		assertThat(eventScraps.existsByUser_IdAndEvent_Id(userId(user), eventId)).isFalse();
	}

	@Test
	void legacyLeaderCountAndMissingProfileAreHandledInBatchSummary() throws Exception {
		MockHttpSession leader = sessionFor("scrap-legacy-leader@mju.ac.kr");
		MockHttpSession participant = sessionFor("scrap-legacy-participant@mju.ac.kr");
		MockHttpSession user = sessionFor("scrap-legacy-user@mju.ac.kr");
		User leaderUser = users.findById(userId(leader)).orElseThrow();
		StudyGroup group = new StudyGroup(leaderUser, "레거시 저장 모임", "소개", 3, "매주", "서울");
		group.replaceRoles(List.of());
		long groupId = groups.saveAndFlush(group).getId();
		members.saveAndFlush(new GroupMember(group, users.findById(userId(participant)).orElseThrow()));
		mvc.perform(put("/api/scraps/groups/{groupId}", groupId).session(user))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/scraps/me").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].currentMemberCount").value(2))
				.andExpect(jsonPath("$[0].leaderName").isEmpty())
				.andExpect(jsonPath("$[0].leaderAvatarUrl").isEmpty());
	}

	@Test
	void saveRequiresExistingTargetAndEveryEndpointRequiresExistingSessionUser() throws Exception {
		MockHttpSession user = sessionFor("scrap-errors-user@mju.ac.kr");
		MockHttpSession invalidUser = new MockHttpSession();
		invalidUser.setAttribute(AuthController.SESSION_USER_ID, Long.MAX_VALUE);

		mvc.perform(put("/api/scraps/groups/{groupId}", Long.MAX_VALUE).session(user))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
		mvc.perform(put("/api/scraps/events/{eventId}", Long.MAX_VALUE).session(user))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
		mvc.perform(get("/api/scraps/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(put("/api/scraps/groups/{groupId}", 1))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(delete("/api/scraps/events/{eventId}", 1).session(invalidUser))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(get("/api/scraps/me").session(invalidUser))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	private void runConcurrently(int count, ThrowingRunnable action) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(count);
		try {
			List<Future<Void>> results = java.util.stream.IntStream.range(0, count)
					.mapToObj(index -> executor.submit((java.util.concurrent.Callable<Void>) () -> {
						start.await();
						action.run();
						return null;
					}))
					.toList();
			start.countDown();
			for (Future<Void> result : results) result.get();
		} finally {
			executor.shutdownNow();
		}
	}

	private long createGroup(MockHttpSession session, String title, int maxMemberCount) throws Exception {
		String body = "{\"title\":\"" + title + "\",\"description\":\"모임 소개\","
				+ "\"maxMemberCount\":" + maxMemberCount + ",\"meetingRule\":\"매주 토요일\","
				+ "\"location\":\"서울\",\"recruitingRoles\":[]}";
		MvcResult result = mvc.perform(post("/api/groups").session(session).contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.groupId")).longValue();
	}

	private long createEvent(MockHttpSession session, String title) throws Exception {
		String body = "{\"title\":\"" + title + "\",\"description\":\"행사 소개\","
				+ "\"organizer\":\"CampusLink\",\"applicationDeadlineAt\":\"2026-08-01T00:00:00Z\","
				+ "\"startsAt\":\"2026-08-02T00:00:00Z\",\"endsAt\":\"2026-08-03T00:00:00Z\","
				+ "\"location\":\"서울\",\"relatedUrl\":\"https://example.com/scrap-test\",\"tags\":[]}";
		MvcResult result = mvc.perform(post("/api/events").session(session).contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isCreated())
				.andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.eventId")).longValue();
	}

	private MockHttpSession sessionFor(String email) {
		User user = users.findByEmail(email).orElseGet(() ->
				users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123"))));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private void saveProfile(MockHttpSession session, String name, String avatarUrl) {
		User user = users.findById(userId(session)).orElseThrow();
		profiles.saveAndFlush(new Profile(user, name, "명지대학교", "컴퓨터공학과",
				"서울", null, avatarUrl));
	}

	private long userId(MockHttpSession session) {
		return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
