package com.mju.mjuton.scrap.service;

import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.scrap.domain.EventScrap;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "EventScrapItem", description = "저장한 해커톤·행사")
public record EventScrapItem(
		@Schema(allowableValues = "HACKATHON") String category,
		Long eventId,
		String title,
		String organizer,
		Instant applicationDeadlineAt,
		Instant startsAt,
		Instant endsAt,
		String location,
		Instant scrappedAt) implements ScrapItem {
	static EventScrapItem from(EventScrap scrap) {
		Event event = scrap.getEvent();
		return new EventScrapItem("HACKATHON", event.getId(), event.getTitle(), event.getOrganizer(),
				event.getApplicationDeadlineAt(), event.getStartsAt(), event.getEndsAt(), event.getLocation(),
				scrap.getCreatedAt());
	}
}
