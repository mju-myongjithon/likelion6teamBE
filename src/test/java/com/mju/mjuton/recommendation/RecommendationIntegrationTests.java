package com.mju.mjuton.recommendation;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.event.service.EventService;
import com.mju.mjuton.event.service.EventService.EventValues;
import com.mju.mjuton.group.domain.GroupJoinApplication;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.domain.StudyGroup.RoleValues;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.group.service.GroupService;
import com.mju.mjuton.group.service.GroupService.GroupDetail;
import com.mju.mjuton.group.service.GroupService.GroupValues;
import com.mju.mjuton.profile.service.ProfileService;
import com.mju.mjuton.profile.service.ProfileService.ProfileValues;
import com.mju.mjuton.recommendation.service.RecommendationAiClient;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.AiAssessment;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateInput;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateKey;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.ProfileInput;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RecommendationIntegrationTests {
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ProfileService profileService;
	@Autowired GroupService groupService;
	@Autowired EventService eventService;
	@Autowired StudyGroupRepository groups;
	@Autowired EventRepository events;
	@Autowired GroupJoinApplicationRepository applications;
	@Autowired FakeRecommendationAiClient aiClient;

	@BeforeEach
	void clearCandidates() {
		events.deleteAll();
		groups.deleteAll();
		aiClient.available = true;
	}

	@Test
	void combinesRuleAndAiScoresAndSupportsCategoryFilters() throws Exception {
		User user = profileUser("AI", "해커톤", "백엔드 개발자");
		User leader = user("recommendation-leader");
		long groupId = createGroup(leader, "AI 백엔드 스터디", 5,
				List.of(new RoleValues("백엔드 개발자", "Spring")));
		long eventId = createEvent(leader, "AI 서비스 해커톤", Instant.parse("2099-08-01T00:00:00Z"),
				List.of("AI", "백엔드"));
		MockHttpSession session = session(user);

		mvc.perform(get("/api/recommendations").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].score", everyItem(greaterThanOrEqualTo(0))))
				.andExpect(jsonPath("$[*].score", everyItem(lessThanOrEqualTo(100))))
				.andExpect(jsonPath("$[*].aiScore", everyItem(lessThanOrEqualTo(100))))
				.andExpect(jsonPath("$[*].mode", everyItem(org.hamcrest.Matchers.is("HYBRID"))))
				.andExpect(jsonPath("$[*].reasons[0]",
						everyItem(org.hamcrest.Matchers.is("AI가 의미 적합성을 확인했어요."))));

		mvc.perform(get("/api/recommendations").session(session).param("filter", "STUDY"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].category").value("STUDY"))
				.andExpect(jsonPath("$[0].targetId").value(groupId));
		mvc.perform(get("/api/recommendations").session(session).param("filter", "HACKATHON"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].category").value("HACKATHON"))
				.andExpect(jsonPath("$[0].targetId").value(eventId));
	}

	@Test
	void excludesUnavailableAndAlreadyRelatedCandidates() throws Exception {
		User user = profileUser("백엔드", "스터디", "개발자");
		User leader = user("candidate-leader");
		createGroup(leader, "정원 마감", 1, List.of());
		createGroup(user, "내가 만든 모임", 5, List.of());
		long pendingId = createGroup(leader, "신청 대기 모임", 5, List.of());
		applications.saveAndFlush(new GroupJoinApplication(groups.findById(pendingId).orElseThrow(), user));
		long closedId = createGroup(leader, "닫힌 모임", 5, List.of());
		StudyGroup closed = groups.findById(closedId).orElseThrow();
		closed.closeRecruitment();
		groups.saveAndFlush(closed);
		long eligibleGroupId = createGroup(leader, "백엔드 개발 스터디", 5,
				List.of(new RoleValues("개발자", "Spring")));
		createEvent(leader, "마감 행사", Instant.parse("2020-01-01T00:00:00Z"), List.of("백엔드"));
		long eligibleEventId = createEvent(leader, "백엔드 해커톤",
				Instant.parse("2099-08-01T00:00:00Z"), List.of("백엔드"));

		mvc.perform(get("/api/recommendations").session(session(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[*].targetId",
						org.hamcrest.Matchers.containsInAnyOrder((int) eligibleGroupId, (int) eligibleEventId)));
	}

	@Test
	void fallsBackToRuleScoresAndValidatesAuthenticationAndLimit() throws Exception {
		User user = profileUser("Spring", "스터디", "백엔드");
		User leader = user("fallback-leader");
		createGroup(leader, "Spring 백엔드 스터디", 5,
				List.of(new RoleValues("백엔드", "Spring")));
		aiClient.available = false;

		mvc.perform(get("/api/recommendations").session(session(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mode").value("RULE_FALLBACK"))
				.andExpect(jsonPath("$[0].aiScore").value(org.hamcrest.Matchers.nullValue()))
				.andExpect(jsonPath("$[0].score").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$[0].reasons").isNotEmpty());
		mvc.perform(get("/api/recommendations")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/recommendations").session(session(user)).param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private User profileUser(String interest, String purpose, String role) {
		User user = user("profile");
		profileService.createForSignup(user, new ProfileValues("추천 사용자", "명지대학교", "컴퓨터공학과",
				"서울", "AI 서비스를 만들고 싶어요.", null,
				List.of(interest), List.of(purpose), List.of(role)));
		return user;
	}

	private User user(String prefix) {
		String email = prefix + "-" + SEQUENCE.incrementAndGet() + "@mju.ac.kr";
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}

	private long createGroup(User leader, String title, int maxMemberCount, List<RoleValues> roles) {
		GroupDetail group = groupService.create(leader.getId(), new GroupValues(null, title, "모임 상세 소개",
				maxMemberCount, "매주 토요일", "서울", roles));
		return group.groupId();
	}

	private long createEvent(User creator, String title, Instant deadline, List<String> tags) {
		return eventService.create(creator.getId(), new EventValues(title, "행사 상세 소개", "CampusLink",
				deadline, deadline.plusSeconds(86_400), deadline.plusSeconds(172_800),
				"서울", "https://example.com/recommendation-" + SEQUENCE.incrementAndGet(), tags)).eventId();
	}

	private MockHttpSession session(User user) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	@TestConfiguration
	static class AiTestConfiguration {
		@Bean
		@Primary
		FakeRecommendationAiClient fakeRecommendationAiClient() {
			return new FakeRecommendationAiClient();
		}
	}

	static class FakeRecommendationAiClient implements RecommendationAiClient {
		boolean available = true;

		@Override
		public Map<CandidateKey, AiAssessment> assess(ProfileInput profile, List<CandidateInput> candidates) {
			if (!available) return Map.of();
			Map<CandidateKey, AiAssessment> result = new HashMap<>();
			for (CandidateInput candidate : candidates) {
				result.put(candidate.key(), new AiAssessment(90, List.of("AI가 의미 적합성을 확인했어요.")));
			}
			return result;
		}
	}
}
