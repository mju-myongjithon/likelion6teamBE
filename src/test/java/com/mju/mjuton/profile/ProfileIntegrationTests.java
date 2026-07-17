package com.mju.mjuton.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.profile.repository.TagRepository;
import com.mju.mjuton.profile.service.ProfileService;
import com.mju.mjuton.profile.service.ProfileWriteLock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ProfileRepository profiles;
	@Autowired TagRepository tags;
	@Autowired ProfileService profileService;
	@Autowired ProfileWriteLock profileWriteLock;

	@Test
	void createReadAndUpdateProfile() throws Exception {
		MockHttpSession session = sessionFor("profile-flow@mju.ac.kr");
		createProfile(session, "  홍길동  ", List.of("백엔드", "스프링"), List.of("스터디"), List.of("개발자"));

		mvc.perform(get("/api/profile").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("홍길동"))
				.andExpect(jsonPath("$.departmentName").value("컴퓨터공학과"))
				.andExpect(jsonPath("$.interests[0]").value("백엔드"))
				.andExpect(jsonPath("$.grade").doesNotExist());

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(request("김명지", "[안드로이드]", "[]", "[개발자,팀장]")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("김명지"))
				.andExpect(jsonPath("$.purposes").isEmpty());

		mvc.perform(get("/api/profile").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.interests[0]").value("안드로이드"))
				.andExpect(jsonPath("$.roles[0]").value("개발자"))
				.andExpect(jsonPath("$.roles[1]").value("팀장"));
	}

	@Test
	void requiresSessionAndReturnsProfileStateErrors() throws Exception {
		mvc.perform(get("/api/profile"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

		MockHttpSession session = sessionFor("profile-state@mju.ac.kr");
		mvc.perform(get("/api/profile").session(session))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));
		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(request("홍길동", "[]", "[]", "[]")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"));

		mvc.perform(post("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(request("홍길동", "[]", "[]", "[]")))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
				.andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void validatesRequiredFieldsAndNormalizedDuplicateTags() throws Exception {
		MockHttpSession session = sessionFor("profile-validation@mju.ac.kr");
		createProfile(session, "홍길동", List.of(), List.of(), List.of());

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(request("   ", "[]", "[]", "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(request("홍길동", "[백엔드, 백엔드 ]", "[]", "[]")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"홍길동\",\"schoolName\":\"명지대학교\","
						+ "\"departmentName\":\"컴퓨터공학과\",\"residenceArea\":\"서울\","
						+ "\"purposes\":[],\"roles\":[]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void updatesAndReturnsResidenceCoordinates() throws Exception {
		MockHttpSession session = sessionFor("profile-location@mju.ac.kr");
		createProfile(session, "홍길동", List.of(), List.of(), List.of());

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(requestWithResidenceCoordinates("홍길동", 37.5665, 126.9780)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.residenceLatitude").value(37.5665))
				.andExpect(jsonPath("$.residenceLongitude").value(126.9780));

		mvc.perform(get("/api/profile").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.residenceLatitude").value(37.5665))
				.andExpect(jsonPath("$.residenceLongitude").value(126.9780));
	}

	@Test
	void rejectsInvalidResidenceCoordinates() throws Exception {
		MockHttpSession session = sessionFor("profile-location-invalid@mju.ac.kr");
		createProfile(session, "홍길동", List.of(), List.of(), List.of());

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(requestWithResidenceCoordinates("홍길동", 37.5665, null)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mvc.perform(put("/api/profile").session(session).contentType(MediaType.APPLICATION_JSON)
				.content(requestWithResidenceCoordinates("홍길동", 91.0, 126.9780)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void normalizesTagsByType() throws Exception {
		MockHttpSession session = sessionFor("profile-tags@mju.ac.kr");
		createProfile(session, "홍길동", List.of("개발"), List.of("개발"), List.of());

		assertThat(tags.findByTypeAndName(TagType.INTEREST, "개발")).isPresent();
		assertThat(tags.findByTypeAndName(TagType.PURPOSE, "개발")).isPresent();
	}

	@Test
	void concurrentProfilesCanCreateTheSameNewTag() throws Exception {
		User first = user("profile-parallel-1@mju.ac.kr");
		User second = user("profile-parallel-2@mju.ac.kr");
		ProfileService.ProfileValues values = new ProfileService.ProfileValues("홍길동", "명지대학교",
				"컴퓨터공학과", "서울", null, null, null, null, List.of("동시성"), List.of(), List.of());
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> firstResult = executor.submit(() -> createAfter(start, first.getId(), values));
			Future<?> secondResult = executor.submit(() -> createAfter(start, second.getId(), values));
			start.countDown();
			firstResult.get();
			secondResult.get();
			assertThat(profiles.findById(first.getId())).isPresent();
			assertThat(profiles.findById(second.getId())).isPresent();
			assertThat(tags.findByTypeAndName(TagType.INTEREST, "동시성")).isPresent();
		} finally {
			executor.shutdownNow();
		}
	}

	private void createAfter(CountDownLatch start, long userId, ProfileService.ProfileValues values) {
		try {
			start.await();
			User user = users.findById(userId).orElseThrow();
			profileWriteLock.execute(() -> profileService.createForSignup(user, values));
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(exception);
		}
	}

	private MockHttpSession sessionFor(String email) {
		User user = user(email);
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private User user(String email) {
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}

	private void createProfile(MockHttpSession session, String name, List<String> interests, List<String> purposes,
			List<String> roles) {
		long userId = (Long) session.getAttribute(AuthController.SESSION_USER_ID);
		User user = users.findById(userId).orElseThrow();
		ProfileService.ProfileValues values = new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과",
				"서울", null, null, "백엔드 개발자", null, interests, purposes, roles);
		profileWriteLock.execute(() -> profileService.createForSignup(user, values));
	}

	private String request(String name, String interests, String purposes, String roles) {
		return "{\"name\":\"" + name + "\",\"schoolName\":\"명지대학교\","
				+ "\"departmentName\":\"컴퓨터공학과\",\"residenceArea\":\"서울\","
				+ "\"bio\":\"백엔드 개발자\",\"avatarUrl\":null,"
				+ "\"interests\":" + jsonArray(interests) + ",\"purposes\":" + jsonArray(purposes)
				+ ",\"roles\":" + jsonArray(roles) + "}";
	}

	private String requestWithResidenceCoordinates(String name, Double latitude, Double longitude) {
		return "{\"name\":\"" + name + "\",\"schoolName\":\"명지대학교\","
				+ "\"departmentName\":\"컴퓨터공학과\",\"residenceArea\":\"서울\","
				+ "\"residenceLatitude\":" + jsonNumber(latitude)
				+ ",\"residenceLongitude\":" + jsonNumber(longitude)
				+ ",\"bio\":\"백엔드 개발자\",\"avatarUrl\":null,"
				+ "\"interests\":[],\"purposes\":[],\"roles\":[]}";
	}

	private String jsonNumber(Double value) {
		return value == null ? "null" : value.toString();
	}

	private String jsonArray(String compact) {
		if (compact.equals("[]")) return compact;
		String body = compact.substring(1, compact.length() - 1);
		return "[\"" + body.replace(",", "\",\"") + "\"]";
	}
}
