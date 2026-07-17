package com.mju.mjuton.listing.service;

import com.mju.mjuton.event.service.EventService.EventSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "HackathonListingItem", description = "해커톤·행사 목록 항목")
public record HackathonListingItem(
		@Schema(allowableValues = "HACKATHON") String category,
		Long eventId,
		String title,
		Instant applicationDeadlineAt,
		Instant startsAt,
		String location,
		Instant createdAt) implements ListingItem {
	static HackathonListingItem from(EventSummary event) {
		return new HackathonListingItem("HACKATHON", event.eventId(), event.title(), event.applicationDeadlineAt(),
				event.startsAt(), event.location(), event.createdAt());
	}
}
