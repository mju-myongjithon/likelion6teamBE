package com.mju.mjuton.scrap.service;

import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.scrap.domain.GroupScrap;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "StudyScrapItem", description = "저장한 스터디 모임")
public record StudyScrapItem(
		@Schema(allowableValues = "STUDY") String category,
		Long groupId,
		String title,
		Long leaderUserId,
		@Schema(nullable = true) String leaderName,
		@Schema(nullable = true) String leaderAvatarUrl,
		String location,
		String meetingRule,
		int maxMemberCount,
		long currentMemberCount,
		Instant scrappedAt) implements ScrapItem {
	static StudyScrapItem from(GroupScrap scrap, long currentMemberCount, Profile leaderProfile) {
		StudyGroup group = scrap.getGroup();
		return new StudyScrapItem("STUDY", group.getId(), group.getTitle(), group.getLeaderUserId(),
				leaderProfile == null ? null : leaderProfile.getName(),
				leaderProfile == null ? null : leaderProfile.getAvatarUrl(),
				group.getLocation(), group.getMeetingRule(), group.getMaxMemberCount(),
				currentMemberCount, scrap.getCreatedAt());
	}
}
