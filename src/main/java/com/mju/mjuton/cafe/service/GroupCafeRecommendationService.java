package com.mju.mjuton.cafe.service;

import com.mju.mjuton.cafe.service.CafeRecommendationService.CafeRecommendationResponse;
import com.mju.mjuton.cafe.service.CafeRecommendationService.RecommendationValues;
import com.mju.mjuton.cafe.service.CafeRecommendationService.UserLocation;
import com.mju.mjuton.cafe.service.ResidenceCoordinateResolver.Coordinate;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GroupCafeRecommendationService {
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final ProfileRepository profiles;
	private final CafeRecommendationService cafeRecommendationService;
	private final ResidenceCoordinateResolver residenceCoordinateResolver;

	public GroupCafeRecommendationService(StudyGroupRepository groups, GroupMemberRepository members,
			ProfileRepository profiles, CafeRecommendationService cafeRecommendationService,
			ResidenceCoordinateResolver residenceCoordinateResolver) {
		this.groups = groups;
		this.members = members;
		this.profiles = profiles;
		this.cafeRecommendationService = cafeRecommendationService;
		this.residenceCoordinateResolver = residenceCoordinateResolver;
	}

	public CafeRecommendationResponse recommend(long groupId, long requesterUserId) {
		StudyGroup group = groups.findById(groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임을 찾을 수 없습니다."));
		if (!isMember(group, requesterUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED", "모임 참여자만 요청할 수 있습니다.");
		}
		List<UserLocation> locations = memberLocations(group);
		return cafeRecommendationService.recommend(new RecommendationValues(locations, null));
	}

	private boolean isMember(StudyGroup group, long userId) {
		return group.getLeaderUserId() == userId || members.existsByGroup_IdAndUser_Id(group.getId(), userId);
	}

	private List<UserLocation> memberLocations(StudyGroup group) {
		List<Long> userIds = memberUserIds(group);
		if (userIds.size() > 20) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
					"사용자 위치는 최대 20개까지 입력할 수 있습니다.");
		}
		Map<Long, Profile> profileByUserId = profiles.findAllById(userIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
		Map<String, Coordinate> resolvedByArea = new HashMap<>();
		List<UserLocation> locations = new ArrayList<>();
		for (Long userId : userIds) {
			Profile profile = profileByUserId.get(userId);
			if (profile == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
						"모든 모임 참여자에게 좌표 또는 해석 가능한 거주 지역이 필요합니다.");
			}
			Double latitude = profile.getResidenceLatitude();
			Double longitude = profile.getResidenceLongitude();
			if (latitude == null && longitude == null) {
				String canonicalArea = canonicalArea(profile.getResidenceArea());
				Coordinate resolved = resolvedByArea.computeIfAbsent(canonicalArea,
						area -> residenceCoordinateResolver.resolve(area).orElse(null));
				if (resolved == null) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
							"모든 모임 참여자에게 좌표 또는 해석 가능한 거주 지역이 필요합니다.");
				}
				latitude = resolved.latitude();
				longitude = resolved.longitude();
			} else if (latitude == null || longitude == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
						"모든 모임 참여자에게 좌표 또는 해석 가능한 거주 지역이 필요합니다.");
			}
			locations.add(new UserLocation(userId, latitude, longitude));
		}
		if (locations.size() < 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
					"모임 참여자 2명 이상의 위치가 필요합니다.");
		}
		return locations;
	}

	private String canonicalArea(String residenceArea) {
		return residenceArea.trim().replace('·', ' ').replaceAll("\\s+", " ");
	}

	private List<Long> memberUserIds(StudyGroup group) {
		Set<Long> userIds = new LinkedHashSet<>();
		userIds.add(group.getLeaderUserId());
		userIds.addAll(members.findUserIdsByGroupId(group.getId()));
		return List.copyOf(userIds);
	}
}
