package com.mju.mjuton.mypage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.eventapplication.domain.EventApplication;
import com.mju.mjuton.eventapplication.repository.EventApplicationRepository;
import com.mju.mjuton.group.domain.GroupJoinApplication;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.repository.MeetupRepository;
import java.time.Instant;
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
	@Autowired EventRepository events;
	@Autowired EventApplicationRepository eventApplications;

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
		Event event = events.saveAndFlush(new Event(leader, "신청한 해커톤", "마이페이지 행사 신청 표시를 검증합니다.",
				"테스트 주최", Instant.parse("2099-02-01T14:59:59Z"),
				Instant.parse("2099-02-15T00:00:00Z"), Instant.parse("2099-02-16T09:00:00Z"),
				"서울", "https://example.com/applied-event"));
		eventApplications.saveAndFlush(new EventApplication(participant, event));
		group(participant, "내가 만든 모임");

		mvc.perform(get("/api/mypage")
						.param("year", "2099")
						.param("month", "2")
						.session(sessionFor(participant)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participatedGroupCount").value(2))
				.andExpect(jsonPath("$.aiMatchSuccessRate").value(50))
				.andExpect(jsonPath("$.monthlyActivityCount").value(1))
				.andExpect(jsonPath("$.activities[0].name").value("월간 정기 모임"))
				.andExpect(jsonPath("$.activities[0].date").value("2099-02-07"))
				.andExpect(jsonPath("$.appliedEvents[0].title").value("신청한 해커톤"))
				.andExpect(jsonPath("$.appliedEvents[0].startsAt").value("2099-02-15T00:00:00Z"))
				.andExpect(jsonPath("$.myGroups.length()").value(2))
				.andExpect(jsonPath("$.myGroups[?(@.title == '내가 만든 모임')].leader").value(true))
				.andExpect(jsonPath("$.myGroups[?(@.title == '참여 모임')].leader").value(false));
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
