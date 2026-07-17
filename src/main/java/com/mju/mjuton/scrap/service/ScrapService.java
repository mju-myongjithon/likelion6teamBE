package com.mju.mjuton.scrap.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository.GroupMemberCount;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.scrap.domain.EventScrap;
import com.mju.mjuton.scrap.domain.GroupScrap;
import com.mju.mjuton.scrap.repository.EventScrapRepository;
import com.mju.mjuton.scrap.repository.GroupScrapRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScrapService {
	private static final Comparator<ScrapItem> ORDER = Comparator
			.comparing(ScrapItem::scrappedAt).reversed()
			.thenComparing(ScrapItem::category)
			.thenComparing(Comparator.comparingLong(ScrapService::targetId).reversed());

	private final GroupScrapRepository groupScraps;
	private final EventScrapRepository eventScraps;
	private final StudyGroupRepository groups;
	private final EventRepository events;
	private final GroupMemberRepository members;
	private final ProfileRepository profiles;
	private final UserRepository users;

	public ScrapService(GroupScrapRepository groupScraps, EventScrapRepository eventScraps,
			StudyGroupRepository groups, EventRepository events, GroupMemberRepository members,
			ProfileRepository profiles, UserRepository users) {
		this.groupScraps = groupScraps;
		this.eventScraps = eventScraps;
		this.groups = groups;
		this.events = events;
		this.members = members;
		this.profiles = profiles;
		this.users = users;
	}

	@Transactional
	public void saveGroup(long userId, long groupId) {
		User user = lockedUser(userId);
		StudyGroup group = groups.findById(groupId).orElseThrow(ScrapService::groupNotFound);
		if (!groupScraps.existsByUser_IdAndGroup_Id(userId, groupId)) {
			groupScraps.saveAndFlush(new GroupScrap(user, group));
		}
	}

	@Transactional
	public void removeGroup(long userId, long groupId) {
		lockedUser(userId);
		groupScraps.deleteByUser_IdAndGroup_Id(userId, groupId);
	}

	@Transactional
	public void saveEvent(long userId, long eventId) {
		User user = lockedUser(userId);
		Event event = events.findById(eventId).orElseThrow(ScrapService::eventNotFound);
		if (!eventScraps.existsByUser_IdAndEvent_Id(userId, eventId)) {
			eventScraps.saveAndFlush(new EventScrap(user, event));
		}
	}

	@Transactional
	public void removeEvent(long userId, long eventId) {
		lockedUser(userId);
		eventScraps.deleteByUser_IdAndEvent_Id(userId, eventId);
	}

	@Transactional(readOnly = true)
	public List<ScrapItem> findMine(long userId) {
		findUser(userId);
		List<GroupScrap> foundGroups = groupScraps.findByUser_IdOrderByCreatedAtDescIdDesc(userId);
		List<EventScrap> foundEvents = eventScraps.findByUser_IdOrderByCreatedAtDescIdDesc(userId);
		Map<Long, GroupMemberCount> counts = memberCounts(foundGroups);
		Map<Long, Profile> leaderProfiles = leaderProfiles(foundGroups);
		List<ScrapItem> items = new ArrayList<>(foundGroups.stream()
				.map(scrap -> StudyScrapItem.from(scrap, currentMemberCount(scrap.getGroup(), counts),
						leaderProfiles.get(scrap.getGroup().getLeaderUserId())))
				.map(ScrapItem.class::cast)
				.toList());
		items.addAll(foundEvents.stream().map(EventScrapItem::from).map(ScrapItem.class::cast).toList());
		items.sort(ORDER);
		return List.copyOf(items);
	}

	private User lockedUser(long userId) {
		return users.findByIdForUpdate(userId).orElseThrow(ScrapService::authenticationRequired);
	}

	private User findUser(long userId) {
		return users.findById(userId).orElseThrow(ScrapService::authenticationRequired);
	}

	private Map<Long, GroupMemberCount> memberCounts(List<GroupScrap> found) {
		if (found.isEmpty()) return Map.of();
		List<Long> groupIds = found.stream().map(GroupScrap::getGroupId).toList();
		return members.countMembersByGroupIds(groupIds).stream()
				.collect(Collectors.toMap(GroupMemberCount::getGroupId, Function.identity()));
	}

	private long currentMemberCount(StudyGroup group, Map<Long, GroupMemberCount> counts) {
		GroupMemberCount count = counts.get(group.getId());
		if (count == null) return 1;
		return count.getStoredMemberCount() + (count.getLeaderRowCount() > 0 ? 0 : 1);
	}

	private Map<Long, Profile> leaderProfiles(List<GroupScrap> found) {
		if (found.isEmpty()) return Map.of();
		List<Long> leaderIds = found.stream()
				.map(scrap -> scrap.getGroup().getLeaderUserId())
				.distinct()
				.toList();
		return profiles.findAllById(leaderIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
	}

	private static long targetId(ScrapItem item) {
		if (item instanceof StudyScrapItem study) return study.groupId();
		return ((EventScrapItem) item).eventId();
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException groupNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임이 존재하지 않습니다.");
	}

	private static ApiException eventNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "행사가 존재하지 않습니다.");
	}
}
