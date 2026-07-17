package com.mju.mjuton.listing.service;

import com.mju.mjuton.group.service.GroupService.GroupSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "StudyListingItem", description = "스터디 목록 항목")
public record StudyListingItem(
		@Schema(allowableValues = "STUDY") String category,
		Long groupId,
		String title,
		String location,
		String meetingRule,
		int maxMemberCount,
		Instant createdAt) implements ListingItem {
	static StudyListingItem from(GroupSummary group) {
		return new StudyListingItem("STUDY", group.groupId(), group.title(), group.location(), group.meetingRule(),
				group.maxMemberCount(), group.createdAt());
	}
}
