package com.mju.mjuton.auth.controller;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.service.AuthService;
import com.mju.mjuton.global.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	public static final String SESSION_USER_ID = "userId";
	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/email-verifications")
	ResponseEntity<Void> sendVerification(@Valid @RequestBody EmailRequest request) {
		authService.sendVerification(request.email());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/signup")
	ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest body, HttpServletRequest request) {
		User user = authService.signup(body.email(), body.verificationCode(), body.password());
		startNewSession(request, user.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
	}

	@PostMapping("/login")
	UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
		User user = authService.login(body.email(), body.password());
		startNewSession(request, user.getId());
		return UserResponse.from(user);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) session.invalidate();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/session")
	UserResponse session(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return UserResponse.from(authService.findUser(userId));
	}

	private void startNewSession(HttpServletRequest request, Long userId) {
		HttpSession oldSession = request.getSession(false);
		if (oldSession != null) oldSession.invalidate();
		request.getSession(true).setAttribute(SESSION_USER_ID, userId);
	}

	public record EmailRequest(@NotBlank(message = "이메일은 필수입니다.") String email) {}
	public record SignupRequest(@NotBlank(message = "이메일은 필수입니다.") String email,
			@NotBlank(message = "인증번호는 필수입니다.") String verificationCode,
			@NotBlank(message = "비밀번호는 필수입니다.") String password) {}
	public record LoginRequest(@NotBlank(message = "이메일은 필수입니다.") String email,
			@NotBlank(message = "비밀번호는 필수입니다.") String password) {}
	public record UserResponse(Long userId, String email) {
		static UserResponse from(User user) { return new UserResponse(user.getId(), user.getEmail()); }
	}
}
