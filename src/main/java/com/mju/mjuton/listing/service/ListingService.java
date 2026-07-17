package com.mju.mjuton.listing.service;

import com.mju.mjuton.event.service.EventService;
import com.mju.mjuton.group.service.GroupService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ListingService {
	private static final Comparator<ListingItem> ORDER = Comparator
			.comparing(ListingItem::createdAt).reversed()
			.thenComparing(ListingItem::category)
			.thenComparing(Comparator.comparingLong(ListingService::targetId).reversed());

	private final GroupService groupService;
	private final EventService eventService;

	public ListingService(GroupService groupService, EventService eventService) {
		this.groupService = groupService;
		this.eventService = eventService;
	}

	public List<ListingItem> findAll(ListingFilter filter) {
		return switch (filter) {
			case STUDY -> studies();
			case HACKATHON -> hackathons();
			case ALL -> combined();
		};
	}

	private List<ListingItem> combined() {
		List<ListingItem> items = new ArrayList<>(studies());
		items.addAll(hackathons());
		items.sort(ORDER);
		return items;
	}

	private List<ListingItem> studies() {
		return groupService.findAll().stream().map(StudyListingItem::from).map(ListingItem.class::cast).toList();
	}

	private List<ListingItem> hackathons() {
		return eventService.findAll().stream().map(HackathonListingItem::from).map(ListingItem.class::cast).toList();
	}

	private static long targetId(ListingItem item) {
		if (item instanceof StudyListingItem study) return study.groupId();
		return ((HackathonListingItem) item).eventId();
	}
}
