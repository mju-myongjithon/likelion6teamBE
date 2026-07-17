package com.mju.mjuton.auth.controller;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.service.AuthService;
import com.mju.mjuton.auth.service.SignupService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증", description = "학교 이메일 인증, 회원가입, 로그인·로그아웃과 서버 세션 상태를 관리합니다.")
public class AuthController {
	public static final String SESSION_USER_ID = "userId";
	private final AuthService authService;
	private final SignupService signupService;

	public AuthController(AuthService authService, SignupService signupService) {
		this.authService = authService;
		this.signupService = signupService;
	}

	@PostMapping("/email-verifications")
	@Operation(summary = "학교 이메일 인증번호 발송",
			description = "mju.ac.kr 이메일로 6자리 인증번호를 발송합니다. 같은 이메일은 1분 후 재요청할 수 있습니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "인증번호 발송 성공"),
			@ApiResponse(responseCode = "400", description = "학교 이메일 형식이 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "429", description = "1분 이내 재발송 요청",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "메일 발송 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> sendVerification(@Valid @RequestBody EmailRequest request) {
		authService.sendVerification(request.email());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	@PostMapping("/signup")
	@Operation(summary = "회원가입",
			description = "인증번호를 검증하고 사용자와 프로필을 하나의 트랜잭션으로 생성한 뒤 새 JSESSIONID를 발급합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "회원가입 및 세션 생성 성공",
					content = @Content(schema = @Schema(implementation = UserResponse.class))),
			@ApiResponse(responseCode = "400", description = "인증번호·비밀번호·프로필 입력이 올바르지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		User user = signupService.signup(body.email(), body.verificationCode(), body.password(),
				body.profile().toValues());
		startNewSession(request, user.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
	}

	@PostMapping("/login")
	@Operation(summary = "로그인", description = "이메일과 비밀번호를 검증하고 기존 세션을 폐기한 뒤 새 JSESSIONID를 발급합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 및 세션 생성 성공",
					content = @Content(schema = @Schema(implementation = UserResponse.class))),
			@ApiResponse(responseCode = "400", description = "필수 입력 누락",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	UserResponse login(@Valid @RequestBody LoginRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		User user = authService.login(body.email(), body.password());
		startNewSession(request, user.getId());
		return UserResponse.from(user);
	}

	@PostMapping("/logout")
	@Operation(summary = "로그아웃", description = "현재 세션이 있으면 무효화합니다. 세션이 없어도 성공으로 처리합니다.")
	@ApiResponse(responseCode = "204", description = "로그아웃 처리 완료")
	ResponseEntity<Void> logout(@Parameter(hidden = true) HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) session.invalidate();
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/session")
	@Operation(summary = "현재 세션 확인", description = "JSESSIONID의 userId로 현재 로그인 사용자를 조회합니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 사용자 조회 성공",
					content = @Content(schema = @Schema(implementation = UserResponse.class))),
			@ApiResponse(responseCode = "401", description = "세션이 없거나 유효하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	UserResponse session(@Parameter(hidden = true) HttpServletRequest request) {
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

	@Schema(name = "EmailVerificationRequest", description = "학교 이메일 인증번호 발송 요청")
	public record EmailRequest(
			@Schema(description = "mju.ac.kr 학교 이메일", example = "student@mju.ac.kr")
			@NotBlank(message = "이메일은 필수입니다.") String email) {}
	@Schema(name = "SignupRequest", description = "인증 정보와 프로필을 포함한 회원가입 요청")
	public record SignupRequest(
			@Schema(description = "인증받은 mju.ac.kr 학교 이메일", example = "student@mju.ac.kr")
			@NotBlank(message = "이메일은 필수입니다.") String email,
			@Schema(description = "이메일로 받은 6자리 인증번호", example = "123456", pattern = "\\d{6}")
			@NotBlank(message = "인증번호는 필수입니다.") String verificationCode,
			@Schema(description = "UTF-8 기준 8~72바이트 비밀번호", example = "password123", format = "password")
			@NotBlank(message = "비밀번호는 필수입니다.") String password,
			@NotNull(message = "프로필은 필수입니다.") @Valid SignupProfileRequest profile) {}
	@Schema(name = "SignupProfileRequest", description = "회원가입 시 함께 생성할 프로필")
	public record SignupProfileRequest(
			@Schema(description = "표시 이름", example = "홍길동", minLength = 1, maxLength = 50,
					requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(description = "학교명", example = "명지대학교", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String schoolName,
			@Schema(description = "학과명", example = "컴퓨터공학과", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String departmentName,
			@Schema(description = "거주 지역", example = "서울", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String residenceArea,
			@Schema(description = "거주 지역 대표 위도. 시군구/동 단위 대표 좌표를 저장합니다.", example = "37.5665",
					nullable = true) Double residenceLatitude,
			@Schema(description = "거주 지역 대표 경도. 시군구/동 단위 대표 좌표를 저장합니다.", example = "126.9780",
					nullable = true) Double residenceLongitude,
			@Schema(description = "자기소개. null은 허용하지만 빈 문자열은 허용하지 않습니다.", example = "백엔드 개발자입니다.", maxLength = 500)
			String bio,
			@Schema(description = "프로필 이미지 URL. 파일 업로드는 제공하지 않습니다.", example = "https://example.com/avatar.png", maxLength = 2048)
			String avatarUrl,
			@ArraySchema(arraySchema = @Schema(description = "관심사 태그", example = "[\"백엔드\", \"스프링\"]"),
					schema = @Schema(minLength = 1, maxLength = 50), maxItems = 20, uniqueItems = true)
			@NotNull(message = "관심사 태그 배열은 필수입니다.") List<String> interests,
			@ArraySchema(arraySchema = @Schema(description = "활동 목적 태그", example = "[\"스터디\"]"),
					schema = @Schema(minLength = 1, maxLength = 50), maxItems = 20, uniqueItems = true)
			@NotNull(message = "활동 목적 태그 배열은 필수입니다.") List<String> purposes,
			@ArraySchema(arraySchema = @Schema(description = "역할 태그", example = "[\"개발자\"]"),
					schema = @Schema(minLength = 1, maxLength = 50), maxItems = 20, uniqueItems = true)
			@NotNull(message = "역할 태그 배열은 필수입니다.") List<String> roles) {
		ProfileService.ProfileValues toValues() {
			return new ProfileService.ProfileValues(name, schoolName, departmentName, residenceArea,
					residenceLatitude, residenceLongitude, bio, avatarUrl, interests, purposes, roles);
		}
	}
	@Schema(name = "LoginRequest", description = "로그인 요청")
	public record LoginRequest(
			@Schema(description = "가입한 mju.ac.kr 학교 이메일", example = "student@mju.ac.kr")
			@NotBlank(message = "이메일은 필수입니다.") String email,
			@Schema(description = "비밀번호", example = "password123", format = "password")
			@NotBlank(message = "비밀번호는 필수입니다.") String password) {}
	@Schema(name = "UserResponse", description = "인증된 사용자 기본 정보")
	public record UserResponse(
			@Schema(description = "사용자 식별자", example = "1") Long userId,
			@Schema(description = "사용자 학교 이메일", example = "student@mju.ac.kr") String email) {
		static UserResponse from(User user) { return new UserResponse(user.getId(), user.getEmail()); }
	}
}
