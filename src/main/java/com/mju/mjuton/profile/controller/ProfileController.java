package com.mju.mjuton.profile.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.domain.ProfileTag;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
	private final ProfileService profileService;

	public ProfileController(ProfileService profileService) {
		this.profileService = profileService;
	}

	@PostMapping
	ResponseEntity<ProfileResponse> create(@Valid @RequestBody ProfileRequest body, HttpServletRequest request) {
		Profile profile = profileService.create(sessionUserId(request), body.toValues());
		return ResponseEntity.status(HttpStatus.CREATED).body(ProfileResponse.from(profile));
	}

	@GetMapping
	ProfileResponse find(HttpServletRequest request) {
		return ProfileResponse.from(profileService.find(sessionUserId(request)));
	}

	@PutMapping
	ProfileResponse update(@Valid @RequestBody ProfileRequest body, HttpServletRequest request) {
		return ProfileResponse.from(profileService.update(sessionUserId(request), body.toValues()));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	public record ProfileRequest(String name, String schoolName, String departmentName, String residenceArea,
			String bio, String avatarUrl, @NotNull(message = "관심사 태그 배열은 필수입니다.") List<String> interests,
			@NotNull(message = "활동 목적 태그 배열은 필수입니다.") List<String> purposes,
			@NotNull(message = "역할 태그 배열은 필수입니다.") List<String> roles) {
		ProfileService.ProfileValues toValues() {
			return new ProfileService.ProfileValues(name, schoolName, departmentName, residenceArea, bio, avatarUrl,
					interests, purposes, roles);
		}
	}

	public record ProfileResponse(Long userId, String name, String schoolName, String departmentName,
			String residenceArea, String bio, String avatarUrl, List<String> interests, List<String> purposes,
			List<String> roles, Instant createdAt, Instant updatedAt) {
		static ProfileResponse from(Profile profile) {
			return new ProfileResponse(profile.getUserId(), profile.getName(), profile.getSchoolName(),
					profile.getDepartmentName(), profile.getResidenceArea(), profile.getBio(), profile.getAvatarUrl(),
					names(profile, TagType.INTEREST), names(profile, TagType.PURPOSE), names(profile, TagType.ROLE),
					profile.getCreatedAt(), profile.getUpdatedAt());
		}

		private static List<String> names(Profile profile, TagType type) {
			return profile.getProfileTags().stream().map(ProfileTag::getTag)
					.filter(tag -> tag.getType() == type).map(tag -> tag.getName()).toList();
		}
	}
}
