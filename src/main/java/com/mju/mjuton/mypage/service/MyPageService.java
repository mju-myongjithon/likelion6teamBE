package com.mju.mjuton.mypage.service;

import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
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

	public MyPageService(UserRepository users, StudyGroupRepository groups,
			GroupJoinApplicationRepository applications, MeetupRepository meetups) {
		this.users = users;
		this.groups = groups;
		this.applications = applications;
		this.meetups = meetups;
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
		return new MyPageSummary(participatedGroupCount, aiMatchSuccessRate, activities.size(), activities);
	}

	public record MyPageSummary(long participatedGroupCount, int aiMatchSuccessRate,
			int monthlyActivityCount, List<Activity> activities) {}

	public record Activity(Long meetupId, Long groupId, String name, LocalDate date, LocalTime time,
			MeetupStatus status) {
		static Activity from(Meetup meetup) {
			return new Activity(meetup.getId(), meetup.getGroupId(), meetup.getName(),
					meetup.getMeetingDate(), meetup.getMeetingTime(), meetup.getStatus());
		}
	}
}
