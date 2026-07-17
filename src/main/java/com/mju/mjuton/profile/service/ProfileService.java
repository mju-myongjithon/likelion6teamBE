package com.mju.mjuton.profile.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.domain.Tag;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.profile.repository.TagRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
	private final ProfileRepository profiles;
	private final TagRepository tags;
	private final UserRepository users;
	private final ProfileWriteLock profileWriteLock;

	public ProfileService(ProfileRepository profiles, TagRepository tags, UserRepository users,
			ProfileWriteLock profileWriteLock) {
		this.profiles = profiles;
		this.tags = tags;
		this.users = users;
		this.profileWriteLock = profileWriteLock;
	}

	public Profile createForSignup(User user, ProfileValues values) {
		NormalizedValues normalized = normalize(values);
		Profile profile = new Profile(user, normalized.name(), normalized.schoolName(),
				normalized.departmentName(), normalized.residenceArea(), normalized.residenceLatitude(),
				normalized.residenceLongitude(), normalized.bio(), normalized.avatarUrl());
		profile.replaceTags(resolveTags(normalized));
		return profiles.saveAndFlush(profile);
	}

	@Transactional(readOnly = true)
	public Profile find(long userId) {
		ensureUserExists(userId);
		return profiles.findById(userId).orElseThrow(ProfileService::profileNotFound);
	}

	public Profile update(long userId, ProfileValues values) {
		return profileWriteLock.execute(() -> updateInTransaction(userId, values));
	}

	private Profile updateInTransaction(long userId, ProfileValues values) {
		ensureUserExists(userId);
		Profile profile = profiles.findById(userId).orElseThrow(ProfileService::profileNotFound);
		NormalizedValues normalized = normalize(values);
		profile.update(normalized.name(), normalized.schoolName(), normalized.departmentName(),
				normalized.residenceArea(), normalized.residenceLatitude(), normalized.residenceLongitude(),
				normalized.bio(), normalized.avatarUrl());
		profile.replaceTags(resolveTags(normalized));
		return profiles.saveAndFlush(profile);
	}

	private void ensureUserExists(long userId) {
		if (!users.existsById(userId)) throw authenticationRequired();
	}

	private NormalizedValues normalize(ProfileValues values) {
		String name = required(values.name(), "이름", 50);
		String schoolName = required(values.schoolName(), "학교명", 100);
		String departmentName = required(values.departmentName(), "학과명", 100);
		String residenceArea = required(values.residenceArea(), "거주 지역", 100);
		validateResidenceCoordinates(values.residenceLatitude(), values.residenceLongitude());
		String bio = optional(values.bio(), "자기소개", 500);
		String avatarUrl = optional(values.avatarUrl(), "프로필 이미지 URL", 2048);
		return new NormalizedValues(name, schoolName, departmentName, residenceArea, values.residenceLatitude(),
				values.residenceLongitude(), bio, avatarUrl, tagNames(values.interests(), "관심사"), tagNames(values.purposes(), "활동 목적"),
				tagNames(values.roles(), "역할"));
	}

	private void validateResidenceCoordinates(Double latitude, Double longitude) {
		if (latitude == null && longitude == null) return;
		if (latitude == null || longitude == null
				|| !Double.isFinite(latitude) || !Double.isFinite(longitude)
				|| latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
			throw invalidRequest("거주 지역 좌표가 올바르지 않습니다.");
		}
	}

	private String required(String value, String field, int maxLength) {
		if (value == null) throw invalidRequest(field + "은(는) 필수입니다.");
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw invalidRequest(field + "은(는) 1~" + maxLength + "자여야 합니다.");
		}
		return normalized;
	}

	private String optional(String value, String field, int maxLength) {
		if (value == null) return null;
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw invalidRequest(field + "은(는) 1~" + maxLength + "자여야 합니다.");
		}
		return normalized;
	}

	private List<String> tagNames(List<String> values, String field) {
		if (values == null) throw invalidRequest(field + " 태그 배열은 필수입니다.");
		if (values.size() > 20) throw invalidRequest(field + " 태그는 최대 20개까지 입력할 수 있습니다.");
		List<String> normalized = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		for (String value : values) {
			String name = required(value, field + " 태그", 50);
			if (!unique.add(name)) throw invalidRequest("같은 유형에 중복된 태그를 입력할 수 없습니다.");
			normalized.add(name);
		}
		return normalized;
	}

	private List<Tag> resolveTags(NormalizedValues values) {
		List<Tag> resolved = new ArrayList<>();
		resolveTags(resolved, TagType.INTEREST, values.interests());
		resolveTags(resolved, TagType.PURPOSE, values.purposes());
		resolveTags(resolved, TagType.ROLE, values.roles());
		return resolved;
	}

	private void resolveTags(List<Tag> resolved, TagType type, List<String> names) {
		for (String name : names) {
			resolved.add(tags.findByTypeAndName(type, name).orElseGet(() -> tags.save(new Tag(type, name))));
		}
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException profileNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "프로필이 존재하지 않습니다.");
	}

	public record ProfileValues(String name, String schoolName, String departmentName, String residenceArea,
			Double residenceLatitude, Double residenceLongitude, String bio, String avatarUrl,
			List<String> interests, List<String> purposes, List<String> roles) {}

	private record NormalizedValues(String name, String schoolName, String departmentName, String residenceArea,
			Double residenceLatitude, Double residenceLongitude, String bio, String avatarUrl,
			List<String> interests, List<String> purposes, List<String> roles) {}
}
