package com.mju.mjuton.cafe;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.cafe.service.CafeSearchClient;
import com.mju.mjuton.cafe.service.CafeSearchClient.CafeCandidate;
import com.mju.mjuton.cafe.service.ResidenceCoordinateResolver;
import com.mju.mjuton.cafe.service.ResidenceCoordinateResolver.Coordinate;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.service.ProfileService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GroupCafeRecommendationIntegrationTests.CafeSearchTestConfig.class)
class GroupCafeRecommendationIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository members;
	@Autowired ProfileService profiles;
	@Autowired TestResidenceCoordinateResolver residenceCoordinateResolver;

	@Test
	void recommendsCafesFromGroupMemberResidenceCoordinates() throws Exception {
		MockHttpSession leaderSession = sessionFor("group-cafe-leader@mju.ac.kr");
		User leader = user(leaderSession);
		User member = user("group-cafe-member@mju.ac.kr");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfile(member, "멤버", 37.2250, 127.1910);
		long groupId = createGroup(leader, member);

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", groupId).session(leaderSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendations.length()").value(3))
				.andExpect(jsonPath("$.recommendations[0].rank").value(1))
				.andExpect(jsonPath("$.recommendations[0].location.placeName").value("세 번째 카페"))
				.andExpect(jsonPath("$.recommendations[1].rank").value(2))
				.andExpect(jsonPath("$.recommendations[2].rank").value(3));
	}

	@Test
	void regularMemberCanRequestGroupCafeRecommendations() throws Exception {
		User leader = user("group-cafe-regular-leader@mju.ac.kr");
		User member = user("group-cafe-regular-member@mju.ac.kr");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfile(member, "멤버", 37.2250, 127.1910);
		long groupId = createGroup(leader, member);

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", groupId).session(sessionFor(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendations.length()").value(3));
	}

	@Test
	void nonMemberCannotRequestGroupCafeRecommendations() throws Exception {
		User leader = user("group-cafe-owner@mju.ac.kr");
		User member = user("group-cafe-approved@mju.ac.kr");
		User outsider = user("group-cafe-outsider@mju.ac.kr");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfile(member, "멤버", 37.2250, 127.1910);
		long groupId = createGroup(leader, member);

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", groupId).session(sessionFor(outsider)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_MEMBER_REQUIRED"));
	}

	@Test
	void missingMemberResidenceCoordinatesReturnInvalidRequest() throws Exception {
		MockHttpSession leaderSession = sessionFor("group-cafe-missing-leader@mju.ac.kr");
		User leader = user(leaderSession);
		User member = user("group-cafe-missing-member@mju.ac.kr");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfileWithoutCoordinates(member, "멤버", "해석 불가 지역");
		long groupId = createGroup(leader, member);

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", groupId).session(leaderSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("GROUP_MEMBER_LOCATION_REQUIRED"));
	}

	@Test
	void resolvesDistrictResidenceWhenCoordinatesAreMissing() throws Exception {
		residenceCoordinateResolver.reset();
		MockHttpSession leaderSession = sessionFor("group-cafe-district-leader@mju.ac.kr");
		User leader = user(leaderSession);
		User member = user("group-cafe-district-member@mju.ac.kr");
		createProfileWithoutCoordinates(leader, "리더", "서울특별시 강남구");
		createProfileWithoutCoordinates(member, "멤버", "서울특별시 성동구");
		long groupId = createGroup(leader, member);

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", groupId).session(leaderSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendations.length()").value(3));

		org.assertj.core.api.Assertions.assertThat(residenceCoordinateResolver.callCount()).isEqualTo(2);
	}

	@Test
	void usesExplicitCoordinateAndDeduplicatesSameDistrictResolution() throws Exception {
		residenceCoordinateResolver.reset();
		MockHttpSession leaderSession = sessionFor("group-cafe-mixed-leader@mju.ac.kr");
		User leader = user(leaderSession);
		User first = user("group-cafe-mixed-first@mju.ac.kr");
		User second = user("group-cafe-mixed-second@mju.ac.kr");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfileWithoutCoordinates(first, "첫 멤버", "서울특별시 · 강남구");
		createProfileWithoutCoordinates(second, "둘째 멤버", "서울특별시   강남구");
		StudyGroup group = new StudyGroup(leader, "지도 모임", "카페를 추천합니다.", 5, "오프라인", "서울");
		group.addInitialMember(leader);
		groups.saveAndFlush(group);
		members.saveAndFlush(new GroupMember(group, first));
		members.saveAndFlush(new GroupMember(group, second));

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", group.getId()).session(leaderSession))
				.andExpect(status().isOk());

		org.assertj.core.api.Assertions.assertThat(residenceCoordinateResolver.callCount()).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(residenceCoordinateResolver.lastArea())
				.isEqualTo("서울특별시 강남구");
	}

	@Test
	void rejectsMoreThanTwentyLocationsBeforeResolvingResidenceAreas() throws Exception {
		residenceCoordinateResolver.reset();
		MockHttpSession leaderSession = sessionFor("group-cafe-limit-leader@mju.ac.kr");
		User leader = user(leaderSession);
		createProfileWithoutCoordinates(leader, "리더", "서울특별시 강남구");
		StudyGroup group = new StudyGroup(leader, "대규모 지도 모임", "위치 개수 제한을 검증합니다.",
				25, "오프라인", "서울");
		group.addInitialMember(leader);
		groups.saveAndFlush(group);
		for (int index = 0; index < 20; index++) {
			User member = user("group-cafe-limit-member-" + index + "@mju.ac.kr");
			createProfileWithoutCoordinates(member, "멤버" + index, "서울특별시 강남구");
			members.save(new GroupMember(group, member));
		}
		members.flush();

		mvc.perform(post("/api/groups/{groupId}/cafes/recommendations", group.getId()).session(leaderSession))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		org.assertj.core.api.Assertions.assertThat(residenceCoordinateResolver.callCount()).isZero();
	}

	private long createGroup(User leader, User member) {
		StudyGroup group = new StudyGroup(leader, "지도 모임", "카페를 추천합니다.", 5, "오프라인", "서울");
		group.addInitialMember(leader);
		groups.saveAndFlush(group);
		members.saveAndFlush(new GroupMember(group, member));
		return group.getId();
	}

	private void createProfile(User user, String name, double latitude, double longitude) {
		profiles.createForSignup(user, new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과",
				"경기도 용인시 처인구", latitude, longitude, null, null, List.of("백엔드"), List.of(), List.of()));
	}

	private void createProfileWithoutCoordinates(User user, String name, String residenceArea) {
		profiles.createForSignup(user, new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과",
				residenceArea, null, null, null, null, List.of("백엔드"), List.of(), List.of()));
	}

	private MockHttpSession sessionFor(String email) {
		return sessionFor(user(email));
	}

	private MockHttpSession sessionFor(User user) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private User user(MockHttpSession session) {
		return users.findById((Long) session.getAttribute(AuthController.SESSION_USER_ID)).orElseThrow();
	}

	private User user(String email) {
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}

	@TestConfiguration
	static class CafeSearchTestConfig {
		@Bean
		@Primary
		CafeSearchClient cafeSearchClient() {
			return (latitude, longitude) -> List.of(
					new CafeCandidate("중앙 카페", 37.2230, 127.1888, "명지대 앞", null, null, "확인 필요"),
					new CafeCandidate("두 번째 카페", 37.2240, 127.1895, "명지대 근처", null, null, "확인 필요"),
						new CafeCandidate("세 번째 카페", 37.2250, 127.1910, "명지대 사거리", null, null, "확인 필요"));
		}

		@Bean
		@Primary
		TestResidenceCoordinateResolver residenceCoordinateResolver() {
			return new TestResidenceCoordinateResolver();
		}
	}

	static class TestResidenceCoordinateResolver implements ResidenceCoordinateResolver {
		private final AtomicInteger calls = new AtomicInteger();
		private String lastArea;

		@Override
		public Optional<Coordinate> resolve(String residenceArea) {
			calls.incrementAndGet();
			lastArea = residenceArea;
			if (residenceArea.endsWith("강남구")) return Optional.of(new Coordinate(37.5172, 127.0473));
			if (residenceArea.endsWith("성동구")) return Optional.of(new Coordinate(37.5633, 127.0371));
			return Optional.empty();
		}

		void reset() {
			calls.set(0);
			lastArea = null;
		}

		int callCount() {
			return calls.get();
		}

		String lastArea() {
			return lastArea;
		}
	}
}
