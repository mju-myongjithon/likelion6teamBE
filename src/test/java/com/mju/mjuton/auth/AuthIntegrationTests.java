package com.mju.mjuton.auth;

import com.mju.mjuton.auth.domain.EmailVerification;
import com.mju.mjuton.auth.repository.EmailVerificationRepository;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.auth.service.AuthService;
import com.mju.mjuton.auth.service.SignupService;
import com.mju.mjuton.auth.service.VerificationMailSender;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.profile.repository.TagRepository;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.service.ProfileService;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import com.mju.mjuton.global.ApiException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthIntegrationTests.MailTestConfig.class)
class AuthIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired CapturingMailSender mailSender;
	@Autowired EmailVerificationRepository verifications;
	@Autowired UserRepository users;
	@Autowired AuthService authService;
	@Autowired SignupService signupService;
	@Autowired ProfileRepository profiles;
	@Autowired TagRepository tags;
	@Autowired Environment environment;

	@Test
	void signupSessionLogoutFlow() throws Exception {
		mvc.perform(post("/api/auth/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"student@mju.ac.kr\"}"))
				.andExpect(status().isCreated());

		MockHttpSession original = new MockHttpSession();
		String oldSessionId = original.getId();
		MockHttpSession session = (MockHttpSession) mvc.perform(post("/api/auth/signup").session(original)
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest("student@mju.ac.kr", mailSender.code("student@mju.ac.kr"), "password123")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("student@mju.ac.kr"))
				.andReturn().getRequest().getSession(false);
		assertThat(session.getId()).isNotEqualTo(oldSessionId);
		assertThat(profiles.findById(users.findByEmail("student@mju.ac.kr").orElseThrow().getId())).isPresent();

		mvc.perform(get("/api/auth/session").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("student@mju.ac.kr"));

		mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/session"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void verificationCodeCanBeCheckedBeforeSignupWithoutBeingConsumed() throws Exception {
		String email = "verify@mju.ac.kr";
		mvc.perform(post("/api/auth/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());
		String code = mailSender.code(email);
		String wrongCode = code.equals("000000") ? "111111" : "000000";

		mvc.perform(post("/api/auth/email-verifications/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + wrongCode + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION"));

		mvc.perform(post("/api/auth/email-verifications/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + code + "\"}"))
				.andExpect(status().isNoContent());
		assertThat(verifications.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow().getConsumedAt()).isNull();

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")))
				.andExpect(status().isCreated());
	}

	@Test
	void loginCreatesSessionAndWrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@mju.ac.kr\"}"));
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest("login@mju.ac.kr", mailSender.code("login@mju.ac.kr"), "password123")));

		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@mju.ac.kr\",\"password\":\"wrong-pass\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@mju.ac.kr\",\"password\":\"password123\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").isNumber());
	}

	@Test
	void codeIsHashedCannotBeReusedAndDuplicateEmailReturnsConflict() throws Exception {
		String email = "hash@mju.ac.kr";
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}")).andExpect(status().isCreated());
		String code = mailSender.code(email);
		EmailVerification verification = verifications.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow();
		assertThat(verification.getCodeHash()).isNotEqualTo(code).startsWith("$2");

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		profiles.deleteById(users.findByEmail(email).orElseThrow().getId());
		users.deleteAll(users.findByEmail(email).stream().toList());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION"));
	}

	@Test
	void concurrentResendAllowsOnlyOneIssuance() throws Exception {
		String email = "parallel@mju.ac.kr";
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = executor.submit(() -> sendAfter(start, email));
			Future<String> second = executor.submit(() -> sendAfter(start, email));
			start.countDown();
			assertThat(java.util.List.of(first.get(), second.get()))
					.containsExactlyInAnyOrder("CREATED", "VERIFICATION_RATE_LIMITED");
			assertThat(verifications.findFirstByEmailOrderByCreatedAtDesc(email)).isPresent();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void concurrentSignupConsumesCodeOnlyOnce() throws Exception {
		String email = "consume@mju.ac.kr";
		authService.sendVerification(email);
		String code = mailSender.code(email);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = executor.submit(() -> signupAfter(start, email, code));
			Future<String> second = executor.submit(() -> signupAfter(start, email, code));
			start.countDown();
			java.util.List<String> results = java.util.List.of(first.get(), second.get());
			assertThat(results).contains("CREATED");
			assertThat(results.stream().filter("CREATED"::equals).count()).isEqualTo(1);
			assertThat(users.findByEmail(email)).isPresent();
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void cookieConfigurationIsSafe() throws Exception {
		assertThat(environment.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
		assertThat(environment.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
		String prod = new ClassPathResource("application-prod.properties").getContentAsString(StandardCharsets.UTF_8);
		assertThat(prod).contains("server.servlet.session.cookie.secure=true");
	}

	private String sendAfter(CountDownLatch start, String email) throws InterruptedException {
		start.await();
		try {
			authService.sendVerification(email);
			return "CREATED";
		} catch (ApiException exception) {
			return exception.getCode();
		}
	}

	private String signupAfter(CountDownLatch start, String email, String code) throws InterruptedException {
		start.await();
		try {
			signupService.signup(email, code, "password123", profileValues("홍길동"));
			return "CREATED";
		} catch (ApiException exception) {
			return exception.getCode();
		}
	}

	@Test
	void rejectsNonSchoolEmailAndImmediateResend() throws Exception {
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"student@gmail.com\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SCHOOL_EMAIL"));

		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"resend@mju.ac.kr\"}"))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"resend@mju.ac.kr\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("VERIFICATION_RATE_LIMITED"));
	}

	@Test
	void mailDeliveryFailureRollsBackVerificationAndAllowsImmediateRetry() throws Exception {
		String email = "mail-failure@mju.ac.kr";
		mailSender.failNext();

		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
		assertThat(verifications.findFirstByEmailOrderByCreatedAtDesc(email)).isEmpty();

		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());
		assertThat(mailSender.code(email)).isNotBlank();
		assertThat(verifications.findFirstByEmailOrderByCreatedAtDesc(email)).isPresent();
	}

	@Test
	void signupRejectsInvalidCodeShapeAndPasswordByteLength() throws Exception {
		String email = "boundary@mju.ac.kr";
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, "12345a", "password123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION"));

		String passwordOver72Bytes = "가".repeat(25);
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, mailSender.code(email), passwordOver72Bytes)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
	}

	@Test
	void loginOver72BytesReturnsInvalidCredentials() throws Exception {
		String email = "login-boundary@mju.ac.kr";
		authService.sendVerification(email);
		signupService.signup(email, mailSender.code(email), "password123", profileValues("홍길동"));
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + "a".repeat(73) + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void profileFailureRollsBackUserAndVerificationConsumption() throws Exception {
		String email = "rollback@mju.ac.kr";
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());
		String code = mailSender.code(email);

		var failedSignup = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")
						.replace("\"name\":\"홍길동\"", "\"name\":\"   \"")
						.replace("\"백엔드\"", "\"롤백전용태그\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andReturn();
		assertThat(users.findByEmail(email)).isEmpty();
		assertThat(verifications.findFirstByEmailOrderByCreatedAtDesc(email).orElseThrow().getConsumedAt()).isNull();
		assertThat(tags.findByTypeAndName(TagType.INTEREST, "롤백전용태그")).isEmpty();
		assertThat(failedSignup.getRequest().getSession(false)).isNull();

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(signupRequest(email, code, "password123")))
				.andExpect(status().isCreated());
	}

	@Test
	void signupRequiresProfile() throws Exception {
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"missing-profile@mju.ac.kr\",\"verificationCode\":\"123456\","
						+ "\"password\":\"password123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	private String signupRequest(String email, String code, String password) {
		return "{\"email\":\"" + email + "\",\"verificationCode\":\"" + code + "\",\"password\":\""
				+ password + "\",\"profile\":" + profileJson("홍길동") + "}";
	}

	private String profileJson(String name) {
		return "{\"name\":\"" + name + "\",\"schoolName\":\"명지대학교\","
				+ "\"departmentName\":\"컴퓨터공학과\",\"residenceArea\":\"서울\","
				+ "\"bio\":\"백엔드 개발자\",\"avatarUrl\":null,\"interests\":[\"백엔드\"],"
				+ "\"purposes\":[\"스터디\"],\"roles\":[\"개발자\"]}";
	}

	private ProfileService.ProfileValues profileValues(String name) {
		return new ProfileService.ProfileValues(name, "명지대학교", "컴퓨터공학과", "서울", null, null, null, null,
				java.util.List.of("백엔드"), java.util.List.of(), java.util.List.of());
	}

	@TestConfiguration
	static class MailTestConfig {
		@Bean @Primary CapturingMailSender capturingMailSender() { return new CapturingMailSender(); }
	}

	static class CapturingMailSender implements VerificationMailSender {
		private final Map<String, String> codes = new ConcurrentHashMap<>();
		private final AtomicBoolean failNext = new AtomicBoolean(false);
		String code(String email) { return codes.get(email); }
		void failNext() { failNext.set(true); }
		@Override public void send(String email, String code) {
			if (failNext.getAndSet(false)) throw new IllegalStateException("mail delivery failed");
			codes.put(email, code);
		}
	}
}
