package com.mju.mjuton.eventapplication;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.eventapplication.repository.EventApplicationRepository;
import java.time.Instant;
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
class EventApplicationIntegrationTests {
	private static final AtomicInteger SEQUENCE = new AtomicInteger();

	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired EventRepository events;
	@Autowired EventApplicationRepository applications;

	@Test
	void recordsStatusIdempotentlyAndCancels() throws Exception {
		User user = user("event-application");
		Event event = event(user, Instant.parse("2099-08-01T14:59:59Z"));
		MockHttpSession session = sessionFor(user);
		long before = applications.count();

		mvc.perform(put("/api/events/{eventId}/application", event.getId()).session(session))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/events/{eventId}/application", event.getId()).session(session))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/events/{eventId}/application", event.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applied").value(true));
		org.assertj.core.api.Assertions.assertThat(applications.count()).isEqualTo(before + 1);

		mvc.perform(delete("/api/events/{eventId}/application", event.getId()).session(session))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/events/{eventId}/application", event.getId()).session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.applied").value(false));
	}

	@Test
	void keepsApplicationsPerUserAndDeletesThemWithTheEvent() throws Exception {
		User creator = user("event-application-creator");
		User firstApplicant = user("event-application-first");
		User secondApplicant = user("event-application-second");
		Event event = event(creator, Instant.parse("2099-08-01T14:59:59Z"));
		long before = applications.count();

		mvc.perform(put("/api/events/{eventId}/application", event.getId()).session(sessionFor(firstApplicant)))
				.andExpect(status().isNoContent());
		mvc.perform(put("/api/events/{eventId}/application", event.getId()).session(sessionFor(secondApplicant)))
				.andExpect(status().isNoContent());
		org.assertj.core.api.Assertions.assertThat(applications.count()).isEqualTo(before + 2);

		mvc.perform(delete("/api/events/{eventId}", event.getId()).session(sessionFor(creator)))
				.andExpect(status().isNoContent());
		org.assertj.core.api.Assertions.assertThat(
				applications.existsByUser_IdAndEvent_Id(firstApplicant.getId(), event.getId())).isFalse();
		org.assertj.core.api.Assertions.assertThat(
				applications.existsByUser_IdAndEvent_Id(secondApplicant.getId(), event.getId())).isFalse();
	}

	@Test
	void requiresLoginAndRejectsClosedEvent() throws Exception {
		User user = user("closed-event-application");
		Event event = event(user, Instant.parse("2020-01-01T00:00:00Z"));

		mvc.perform(put("/api/events/{eventId}/application", event.getId()))
				.andExpect(status().isUnauthorized());
		mvc.perform(put("/api/events/{eventId}/application", event.getId()).session(sessionFor(user)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT_APPLICATION_CLOSED"));
		mvc.perform(get("/api/events/{eventId}/application", Long.MAX_VALUE).session(sessionFor(user)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	private Event event(User creator, Instant deadline) {
		return events.saveAndFlush(new Event(creator, "신청 기록 행사", "서비스 내부 신청 기록 테스트입니다.",
				"테스트 주최", deadline, Instant.parse("2099-08-08T00:00:00Z"),
				Instant.parse("2099-08-09T09:00:00Z"), "서울", "https://example.com/event"));
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
