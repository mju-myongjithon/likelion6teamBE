package com.mju.mjuton.meetup;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MeetupIntegrationTests.CafeSearchTestConfig.class)
class MeetupIntegrationTests {
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository members;
	@Autowired ProfileService profiles;

	@Test
	void createsVotesConfirmsAndPublishesMeetupInChat() throws Exception {
		User leader = user("meetup-leader");
		User member = user("meetup-member");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfileWithoutCoordinates(member, "멤버", "서울특별시 강남구");
		long groupId = createGroup(leader, member);

		MvcResult created = mvc.perform(post("/api/groups/{groupId}/meetups", groupId)
						.session(sessionFor(leader))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "토요일 알고리즘 스터디",
								  "meetingDate": "2099-02-01",
								  "meetingTime": "14:00",
								  "placeMode": "MIDPOINT"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.options.length()").value(3))
				.andReturn();
		long meetupId = number(created, "$.meetupId");
		long optionId = number(created, "$.options[0].optionId");

		mvc.perform(put("/api/groups/{groupId}/meetups/{meetupId}/votes/{optionId}",
						groupId, meetupId, optionId).session(sessionFor(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selectedOptionId").value(optionId))
				.andExpect(jsonPath("$.options[0].voteCount").value(1));

		mvc.perform(post("/api/groups/{groupId}/meetups/{meetupId}/confirm", groupId, meetupId)
						.session(sessionFor(leader)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.confirmedOptionId").value(optionId));

		mvc.perform(get("/api/groups/{groupId}/meetups", groupId).session(sessionFor(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].meetupId").value(meetupId))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"));

		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(sessionFor(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].content").value("[[MEETUP:" + meetupId + "]]"));
	}

	@Test
	void onlyMembersCanUseMeetupsAndVoteCanBeCancelled() throws Exception {
		User leader = user("meetup-owner");
		User member = user("meetup-approved");
		User outsider = user("meetup-outsider");
		createProfile(leader, "리더", 37.2210, 127.1860);
		createProfile(member, "멤버", 37.2250, 127.1910);
		createProfile(outsider, "외부인", 37.2200, 127.1800);
		long groupId = createGroup(leader, member);

		mvc.perform(get("/api/groups/{groupId}/meetups", groupId).session(sessionFor(outsider)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_MEMBER_REQUIRED"));

		MvcResult created = mvc.perform(post("/api/groups/{groupId}/meetups", groupId)
						.session(sessionFor(leader))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "직접 지정 약속",
								  "meetingDate": "2099-02-01",
								  "meetingTime": "14:00",
								  "placeMode": "CUSTOM",
								  "customAddress": "서울 강남구 테헤란로"
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.options.length()").value(1))
				.andReturn();
		long meetupId = number(created, "$.meetupId");
		long optionId = number(created, "$.options[0].optionId");

		mvc.perform(put("/api/groups/{groupId}/meetups/{meetupId}/votes/{optionId}",
						groupId, meetupId, optionId).session(sessionFor(member)))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/groups/{groupId}/meetups/{meetupId}/votes/me", groupId, meetupId)
						.session(sessionFor(member)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.selectedOptionId").doesNotExist())
				.andExpect(jsonPath("$.options[0].voteCount").value(0));
	}

	private long number(MvcResult result, String path) throws Exception {
		return com.jayway.jsonpath.JsonPath.<Number>read(result.getResponse().getContentAsString(), path)
				.longValue();
	}

	private long createGroup(User leader, User member) {
		StudyGroup group = new StudyGroup(leader, "약속 모임", "약속 기능을 검증합니다.", 5, "오프라인", "서울");
		group.addInitialMember(leader);
		groups.saveAndFlush(group);
		members.saveAndFlush(new GroupMember(group, member));
		return group.getId();
	}

	private void createProfile(User user, String name, double latitude, double longitude) {
		profiles.createForSignup(user, new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과",
				"경기도 용인시 처인구", latitude, longitude, null, null,
				List.of("백엔드"), List.of(), List.of()));
	}

	private void createProfileWithoutCoordinates(User user, String name, String residenceArea) {
		profiles.createForSignup(user, new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과",
				residenceArea, null, null, null, null, List.of("백엔드"), List.of(), List.of()));
	}

	private MockHttpSession sessionFor(User user) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private User user(String prefix) {
		String email = prefix + "-" + SEQUENCE.incrementAndGet() + "@mju.ac.kr";
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}

	@TestConfiguration
	static class CafeSearchTestConfig {
		@Bean
		@Primary
		CafeSearchClient cafeSearchClient() {
			return (latitude, longitude) -> List.of(
					new CafeCandidate("중앙 카페", 37.2230, 127.1888, "명지대 앞", "031-000-0001", null, "확인 필요"),
					new CafeCandidate("두 번째 카페", 37.2240, 127.1895, "명지대 근처", "031-000-0002", null, "확인 필요"),
						new CafeCandidate("세 번째 카페", 37.2250, 127.1910, "명지대 사거리", "031-000-0003", null, "확인 필요"));
		}

		@Bean
		@Primary
		ResidenceCoordinateResolver residenceCoordinateResolver() {
			return residenceArea -> Optional.of(new Coordinate(37.5172, 127.0473));
		}
	}
}
