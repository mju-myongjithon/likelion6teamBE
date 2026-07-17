package com.mju.mjuton.profile.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.domain.ProfileTag;
import com.mju.mjuton.profile.domain.TagType;
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
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@Tag(name = "프로필", description = "서버 세션으로 현재 사용자를 식별해 본인 프로필을 조회·수정합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class ProfileController {
	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@GetMapping
	@Operation(summary = "내 프로필 조회", description = "JSESSIONID의 userId에 해당하는 프로필과 유형별 태그를 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "프로필 조회 성공",
					content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
			@ApiResponse(responseCode = "401", description = "세션이 없거나 유효하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "프로필이 존재하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ProfileResponse find(@Parameter(hidden = true) HttpServletRequest request) {
		return ProfileResponse.from(profileService.find(sessionUserId(request)));
	}

	@PutMapping
	@Operation(summary = "내 프로필 전체 수정",
			description = "세션 사용자의 프로필 필드와 태그 전체를 교체합니다. 문자열은 앞뒤 공백을 제거해 저장합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "프로필 수정 성공",
					content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
			@ApiResponse(responseCode = "400", description = "필수 필드, 길이, 태그 개수 또는 중복 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "세션이 없거나 유효하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "프로필이 존재하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ProfileResponse update(@Valid @RequestBody ProfileRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return ProfileResponse.from(profileService.update(sessionUserId(request), body.toValues()));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	@Schema(name = "ProfileUpdateRequest", description = "현재 사용자의 프로필 전체 수정 요청")
	public record ProfileRequest(
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

	@Schema(name = "ProfileResponse", description = "현재 사용자의 프로필과 유형별 태그")
	public record ProfileResponse(
			@Schema(description = "사용자 식별자", example = "1") Long userId,
			@Schema(description = "표시 이름", example = "홍길동") String name,
			@Schema(description = "학교명", example = "명지대학교") String schoolName,
			@Schema(description = "학과명", example = "컴퓨터공학과") String departmentName,
			@Schema(description = "거주 지역", example = "서울") String residenceArea,
			@Schema(description = "거주 지역 대표 위도", example = "37.5665", nullable = true) Double residenceLatitude,
			@Schema(description = "거주 지역 대표 경도", example = "126.9780", nullable = true) Double residenceLongitude,
			@Schema(description = "자기소개", example = "백엔드 개발자입니다.") String bio,
			@Schema(description = "프로필 이미지 URL", example = "https://example.com/avatar.png") String avatarUrl,
			@Schema(description = "관심사 태그") List<String> interests,
			@Schema(description = "활동 목적 태그") List<String> purposes,
			@Schema(description = "역할 태그") List<String> roles,
			@Schema(description = "프로필 생성 시각", type = "string", format = "date-time") Instant createdAt,
			@Schema(description = "프로필 수정 시각", type = "string", format = "date-time") Instant updatedAt) {
		static ProfileResponse from(Profile profile) {
			return new ProfileResponse(profile.getUserId(), profile.getName(), profile.getSchoolName(),
					profile.getDepartmentName(), profile.getResidenceArea(), profile.getResidenceLatitude(),
					profile.getResidenceLongitude(), profile.getBio(), profile.getAvatarUrl(),
					names(profile, TagType.INTEREST), names(profile, TagType.PURPOSE), names(profile, TagType.ROLE),
					profile.getCreatedAt(), profile.getUpdatedAt());
		}

		private static List<String> names(Profile profile, TagType type) {
			return profile.getProfileTags().stream().map(ProfileTag::getTag)
					.filter(tag -> tag.getType() == type).map(tag -> tag.getName()).toList();
		}
	}
}
