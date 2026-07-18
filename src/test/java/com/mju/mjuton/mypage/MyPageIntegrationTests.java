package com.mju.mjuton.mypage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.group.domain.GroupJoinApplication;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.repository.MeetupRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MyPageIntegrationTests {
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository members;
	@Autowired GroupJoinApplicationRepository applications;
	@Autowired MeetupRepository meetups;

	@Test
	void returnsCurrentUsersParticipationMatchRateAndMonthlyMeetups() throws Exception {
		User leader = user("mypage-leader");
		User participant = user("mypage-participant");
		StudyGroup joinedGroup = group(leader, "참여 모임");
		members.saveAndFlush(new GroupMember(joinedGroup, participant));

		GroupJoinApplication approved = new GroupJoinApplication(joinedGroup, participant);
		approved.approve();
		applications.saveAndFlush(approved);
		GroupJoinApplication rejected = new GroupJoinApplication(group(user("other-leader"), "거절 모임"), participant);
		rejected.reject();
		applications.saveAndFlush(rejected);

		meetups.saveAndFlush(new Meetup(joinedGroup, leader, "월간 정기 모임",
				LocalDate.of(2099, 2, 7), LocalTime.of(14, 0)));
		Meetup cancelled = new Meetup(joinedGroup, leader, "취소된 모임",
				LocalDate.of(2099, 2, 8), LocalTime.of(14, 0));
		cancelled.cancel();
		meetups.saveAndFlush(cancelled);
		meetups.saveAndFlush(new Meetup(joinedGroup, leader, "다음 달 모임",
				LocalDate.of(2099, 3, 1), LocalTime.of(14, 0)));

		mvc.perform(get("/api/mypage")
						.param("year", "2099")
						.param("month", "2")
						.session(sessionFor(participant)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participatedGroupCount").value(1))
				.andExpect(jsonPath("$.aiMatchSuccessRate").value(50))
				.andExpect(jsonPath("$.monthlyActivityCount").value(1))
				.andExpect(jsonPath("$.activities[0].name").value("월간 정기 모임"))
				.andExpect(jsonPath("$.activities[0].date").value("2099-02-07"));
	}

	@Test
	void requiresLoginAndRejectsInvalidMonth() throws Exception {
		mvc.perform(get("/api/mypage").param("year", "2099").param("month", "2"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		mvc.perform(get("/api/mypage")
						.param("year", "2099")
						.param("month", "13")
						.session(sessionFor(user("invalid-month"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_MONTH"));
	}

	private StudyGroup group(User leader, String title) {
		StudyGroup group = new StudyGroup(leader, title, "마이페이지 통계를 검증합니다.", 5, "매주", "서울");
		group.addInitialMember(leader);
		return groups.saveAndFlush(group);
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
}
