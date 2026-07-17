package com.mju.mjuton.auth.service;

import com.mju.mjuton.auth.domain.EmailVerification;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.EmailVerificationRepository;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AuthService {
	private final UserRepository users;
	private final EmailVerificationRepository verifications;
	private final VerificationMailSender mailSender;
	private final TransactionTemplate transactionTemplate;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
	private final SecureRandom random = new SecureRandom();

	public AuthService(UserRepository users, EmailVerificationRepository verifications,
			VerificationMailSender mailSender, PlatformTransactionManager transactionManager) {
		this.users = users;
		this.verifications = verifications;
		this.mailSender = mailSender;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public synchronized void sendVerification(String rawEmail) {
		String email = normalizeEmail(rawEmail);
		transactionTemplate.executeWithoutResult(status -> createAndSendVerification(email));
	}

	private void createAndSendVerification(String email) {
		if (users.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
		}
		Instant now = Instant.now();
		verifications.findFirstByEmailOrderByCreatedAtDesc(email)
				.filter(latest -> latest.getCreatedAt().plusSeconds(60).isAfter(now))
				.ifPresent(latest -> { throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
						"VERIFICATION_RATE_LIMITED", "인증번호는 1분 후 다시 요청할 수 있습니다."); });

		String code = "%06d".formatted(random.nextInt(1_000_000));
		verifications.saveAndFlush(new EmailVerification(email, passwordEncoder.encode(code), now));
		try {
			mailSender.send(email, code);
		} catch (RuntimeException exception) {
			if (exception instanceof ApiException apiException) throw apiException;
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED",
					"인증번호 이메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요.");
		}
	}

	@Transactional(readOnly = true)
	public void verifyCode(String rawEmail, String code) {
		String email = normalizeEmail(rawEmail);
		if (code == null || !code.matches("\\d{6}")) {
			throw invalidVerification();
		}
		EmailVerification verification = verifications.findFirstByEmailOrderByCreatedAtDesc(email)
				.orElseThrow(() -> invalidVerification());
		Instant now = Instant.now();
		if (verification.getConsumedAt() != null || !verification.getExpiresAt().isAfter(now)
				|| !passwordEncoder.matches(code, verification.getCodeHash())) {
			throw invalidVerification();
		}
	}

	User createVerifiedUser(String rawEmail, String code, String password) {
		String email = normalizeEmail(rawEmail);
		validatePassword(password);
		if (code == null || !code.matches("\\d{6}")) {
			throw invalidVerification();
		}
		if (users.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
		}
		EmailVerification verification = verifications.findFirstWithLockByEmailOrderByCreatedAtDesc(email)
				.orElseThrow(() -> invalidVerification());
		Instant now = Instant.now();
		if (verification.getConsumedAt() != null || !verification.getExpiresAt().isAfter(now)
				|| !passwordEncoder.matches(code, verification.getCodeHash())) {
			throw invalidVerification();
		}
		verification.consume(now);
		try {
			return users.saveAndFlush(new User(email, passwordEncoder.encode(password)));
		} catch (DataIntegrityViolationException exception) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "이미 가입된 이메일입니다.");
		}
	}

	@Transactional(readOnly = true)
	public User login(String rawEmail, String password) {
		String email = normalizeEmail(rawEmail);
		if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw invalidCredentials();
		}
		User user = users.findByEmail(email).orElseThrow(() -> invalidCredentials());
		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw invalidCredentials();
		}
		return user;
	}

	@Transactional(readOnly = true)
	public User findUser(long userId) {
		return users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
	}

	private String normalizeEmail(String rawEmail) {
		String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
		if (!email.matches("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@mju\\.ac\\.kr$")) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHOOL_EMAIL", "mju.ac.kr 학교 이메일만 사용할 수 있습니다.");
		}
		return email;
	}

	private void validatePassword(String password) {
		int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < 8 || bytes > 72) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 UTF-8 기준 8~72바이트여야 합니다.");
		}
	}

	private ApiException invalidVerification() {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERIFICATION", "인증번호가 올바르지 않거나 만료되었습니다.");
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
	}
}
