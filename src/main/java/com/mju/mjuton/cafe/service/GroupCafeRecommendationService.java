package com.mju.mjuton.cafe.service;

import com.mju.mjuton.cafe.service.CafeRecommendationService.CafeRecommendationResponse;
import com.mju.mjuton.cafe.service.CafeRecommendationService.RecommendationValues;
import com.mju.mjuton.cafe.service.CafeRecommendationService.UserLocation;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupCafeRecommendationService {
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final ProfileRepository profiles;
	private final CafeRecommendationService cafeRecommendationService;

	public GroupCafeRecommendationService(StudyGroupRepository groups, GroupMemberRepository members,
			ProfileRepository profiles, CafeRecommendationService cafeRecommendationService) {
		this.groups = groups;
		this.members = members;
		this.profiles = profiles;
		this.cafeRecommendationService = cafeRecommendationService;
	}

	@Transactional(readOnly = true)
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
		Map<Long, Profile> profileByUserId = profiles.findAllById(userIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
		List<UserLocation> locations = new ArrayList<>();
		for (Long userId : userIds) {
			Profile profile = profileByUserId.get(userId);
			if (profile == null || profile.getResidenceLatitude() == null || profile.getResidenceLongitude() == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
						"모든 모임 참여자의 거주 지역 좌표가 필요합니다.");
			}
			locations.add(new UserLocation(userId, profile.getResidenceLatitude(), profile.getResidenceLongitude()));
		}
		if (locations.size() < 2) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_MEMBER_LOCATION_REQUIRED",
					"모임 참여자 2명 이상의 거주 지역 좌표가 필요합니다.");
		}
		return locations;
	}

	private List<Long> memberUserIds(StudyGroup group) {
		Set<Long> userIds = new LinkedHashSet<>();
		userIds.add(group.getLeaderUserId());
		for (GroupMember member : members.findByGroup_IdOrderByJoinedAtAscIdAsc(group.getId())) {
			userIds.add(member.getUserId());
		}
		return List.copyOf(userIds);
	}
}
