package com.mju.mjuton.mypage.service;

import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.eventapplication.domain.EventApplication;
import com.mju.mjuton.eventapplication.repository.EventApplicationRepository;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.domain.MeetupStatus;
import com.mju.mjuton.meetup.repository.MeetupRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPageService {
	private static final Set<GroupJoinApplicationStatus> DECIDED_STATUSES =
			Set.of(GroupJoinApplicationStatus.APPROVED, GroupJoinApplicationStatus.REJECTED);

	private final UserRepository users;
	private final StudyGroupRepository groups;
	private final GroupJoinApplicationRepository applications;
	private final MeetupRepository meetups;
	private final EventApplicationRepository eventApplications;

	public MyPageService(UserRepository users, StudyGroupRepository groups,
			GroupJoinApplicationRepository applications, MeetupRepository meetups,
			EventApplicationRepository eventApplications) {
		this.users = users;
		this.groups = groups;
		this.applications = applications;
		this.meetups = meetups;
		this.eventApplications = eventApplications;
	}

	@Transactional(readOnly = true)
	public MyPageSummary find(long userId, YearMonth month) {
		if (!users.existsById(userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		long participatedGroupCount = groups.findMyGroups(userId).size();
		long decidedCount = applications.countByApplicant_IdAndStatusIn(userId, DECIDED_STATUSES);
		long approvedCount = applications.countByApplicant_IdAndStatusIn(
				userId, Set.of(GroupJoinApplicationStatus.APPROVED));
		int aiMatchSuccessRate = decidedCount == 0 ? 0
				: (int) Math.round(approvedCount * 100.0 / decidedCount);
		List<Activity> activities = meetups.findMineBetween(userId, month.atDay(1), month.atEndOfMonth(),
						MeetupStatus.CANCELLED)
				.stream()
				.map(Activity::from)
				.toList();
		List<AppliedEvent> appliedEvents = eventApplications
				.findByUser_IdOrderByEvent_StartsAtAscIdAsc(userId)
				.stream()
				.map(AppliedEvent::from)
				.toList();
		List<MyGroup> myGroups = groups.findMyGroups(userId).stream()
				.map(group -> MyGroup.from(group, userId))
				.toList();
		return new MyPageSummary(participatedGroupCount, aiMatchSuccessRate, activities.size(), activities,
				appliedEvents, myGroups);
	}

	public record MyPageSummary(long participatedGroupCount, int aiMatchSuccessRate,
			int monthlyActivityCount, List<Activity> activities, List<AppliedEvent> appliedEvents,
			List<MyGroup> myGroups) {}

	public record Activity(Long meetupId, Long groupId, String name, LocalDate date, LocalTime time,
			MeetupStatus status) {
		static Activity from(Meetup meetup) {
			return new Activity(meetup.getId(), meetup.getGroupId(), meetup.getName(),
					meetup.getMeetingDate(), meetup.getMeetingTime(), meetup.getStatus());
		}
	}

	public record AppliedEvent(Long eventId, String title, String organizer, java.time.Instant startsAt,
			java.time.Instant endsAt, String location, String relatedUrl, java.time.Instant appliedAt) {
		static AppliedEvent from(EventApplication application) {
			Event event = application.getEvent();
			return new AppliedEvent(event.getId(), event.getTitle(), event.getOrganizer(), event.getStartsAt(),
					event.getEndsAt(), event.getLocation(), event.getRelatedUrl(), application.getAppliedAt());
		}
	}

	public record MyGroup(Long groupId, String title, String meetingRule, String location, boolean leader) {
		static MyGroup from(com.mju.mjuton.group.domain.StudyGroup group, long userId) {
			return new MyGroup(group.getId(), group.getTitle(), group.getMeetingRule(), group.getLocation(),
					group.getLeaderUserId() == userId);
		}
	}
}
