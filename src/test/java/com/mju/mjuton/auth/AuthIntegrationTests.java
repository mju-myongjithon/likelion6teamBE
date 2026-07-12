package com.mju.mjuton.auth;

import com.mju.mjuton.auth.domain.EmailVerification;
import com.mju.mjuton.auth.repository.EmailVerificationRepository;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.auth.service.AuthService;
import com.mju.mjuton.auth.service.VerificationMailSender;
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
				.content("{\"email\":\"student@mju.ac.kr\",\"verificationCode\":\"" + mailSender.code("student@mju.ac.kr")
						+ "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("student@mju.ac.kr"))
				.andReturn().getRequest().getSession(false);
		assertThat(session.getId()).isNotEqualTo(oldSessionId);

		mvc.perform(get("/api/auth/session").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("student@mju.ac.kr"));

		mvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isNoContent());
		mvc.perform(get("/api/auth/session"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void loginCreatesSessionAndWrongPasswordIsRejected() throws Exception {
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@mju.ac.kr\"}"));
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"login@mju.ac.kr\",\"verificationCode\":\"" + mailSender.code("login@mju.ac.kr")
						+ "\",\"password\":\"password123\"}"));

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
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + code
						+ "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + code
						+ "\",\"password\":\"password123\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		users.deleteAll(users.findByEmail(email).stream().toList());
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + code
						+ "\",\"password\":\"password123\"}"))
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
			authService.signup(email, code, "password123");
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
	void signupRejectsInvalidCodeShapeAndPasswordByteLength() throws Exception {
		String email = "boundary@mju.ac.kr";
		mvc.perform(post("/api/auth/email-verifications").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\"}"))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"12345a\","
						+ "\"password\":\"password123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_VERIFICATION"));

		String passwordOver72Bytes = "가".repeat(25);
		mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"verificationCode\":\"" + mailSender.code(email)
						+ "\",\"password\":\"" + passwordOver72Bytes + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
	}

	@Test
	void loginOver72BytesReturnsInvalidCredentials() throws Exception {
		String email = "login-boundary@mju.ac.kr";
		authService.sendVerification(email);
		authService.signup(email, mailSender.code(email), "password123");
		mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"" + "a".repeat(73) + "\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@TestConfiguration
	static class MailTestConfig {
		@Bean @Primary CapturingMailSender capturingMailSender() { return new CapturingMailSender(); }
	}

	static class CapturingMailSender implements VerificationMailSender {
		private final Map<String, String> codes = new ConcurrentHashMap<>();
		String code(String email) { return codes.get(email); }
		@Override public void send(String email, String code) { codes.put(email, code); }
	}
}
