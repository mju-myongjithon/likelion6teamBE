package com.mju.mjuton.group.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupJoinApplication;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import com.mju.mjuton.group.domain.GroupMember;
import com.mju.mjuton.group.domain.GroupMemberRole;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository.GroupMemberCount;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupMembershipService {
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final GroupJoinApplicationRepository applications;
	private final UserRepository users;
	private final ProfileRepository profiles;

	public GroupMembershipService(StudyGroupRepository groups, GroupMemberRepository members,
			GroupJoinApplicationRepository applications, UserRepository users, ProfileRepository profiles) {
		this.groups = groups;
		this.members = members;
		this.applications = applications;
		this.users = users;
		this.profiles = profiles;
	}

	@Transactional
	public ApplicationResponse apply(long groupId, long applicantUserId) {
		StudyGroup group = lockedGroup(groupId);
		User applicant = findUser(applicantUserId);
		if (!group.isRecruiting()) {
			throw conflict("GROUP_RECRUITMENT_CLOSED", "모집이 마감된 모임입니다.");
		}
		if (isMember(group, applicantUserId)) {
			throw conflict("ALREADY_GROUP_MEMBER", "이미 모임에 참여 중입니다.");
		}

		GroupJoinApplication application = applications.findByGroup_IdAndApplicant_Id(groupId, applicantUserId)
				.map(found -> {
					if (found.isPending()) {
						throw conflict("ALREADY_APPLIED", "이미 승인 대기 중인 신청이 있습니다.");
					}
					found.reapply();
					return found;
				})
				.orElseGet(() -> new GroupJoinApplication(group, applicant));
		return ApplicationResponse.from(applications.saveAndFlush(application));
	}

	@Transactional(readOnly = true)
	public List<ApplicationResponse> pendingApplications(long groupId, long leaderUserId) {
		StudyGroup group = findGroup(groupId);
		requireLeader(group, leaderUserId);
		return applications.findByGroup_IdAndStatusOrderByRequestedAtAscIdAsc(
						groupId, GroupJoinApplicationStatus.PENDING).stream()
				.map(ApplicationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ApplicationResponse myApplication(long groupId, long applicantUserId) {
		findUser(applicantUserId);
		findGroup(groupId);
		return applications.findByGroup_IdAndApplicant_Id(groupId, applicantUserId)
				.map(ApplicationResponse::from)
				.orElseThrow(GroupMembershipService::applicationNotFound);
	}

	@Transactional(readOnly = true)
	public MyApplicationPageResponse myApplications(long applicantUserId,
			GroupJoinApplicationStatus status, int page, int size) {
		findUser(applicantUserId);
		validatePage(page, size);
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("requestedAt"), Sort.Order.desc("id")));
		Page<GroupJoinApplication> found = status == null
				? applications.findByApplicant_Id(applicantUserId, pageable)
				: applications.findByApplicant_IdAndStatus(applicantUserId, status, pageable);
		List<GroupJoinApplication> content = found.getContent();
		List<Long> groupIds = content.stream().map(GroupJoinApplication::getGroupId).toList();
		Map<Long, GroupMemberCount> counts = memberCounts(groupIds);
		Set<Long> memberGroupIds = groupIds.isEmpty() ? Set.of()
				: Set.copyOf(members.findGroupIdsByUserIdAndGroupIds(applicantUserId, groupIds));
		Map<Long, Profile> leaderProfiles = leaderProfiles(content);
		List<MyApplicationResponse> responses = content.stream()
				.map(application -> MyApplicationResponse.from(application,
						isCurrentMember(application.getGroup(), applicantUserId, memberGroupIds),
						currentMemberCount(application.getGroup(), counts),
						leaderProfiles.get(application.getGroup().getLeaderUserId())))
				.toList();
		return new MyApplicationPageResponse(responses, found.getNumber(), found.getSize(),
				found.getTotalElements(), found.getTotalPages(), found.isFirst(), found.isLast());
	}

	@Transactional
	public void cancel(long applicationId, long applicantUserId) {
		findUser(applicantUserId);
		GroupJoinApplication application = applications.findByIdForUpdate(applicationId)
				.filter(found -> found.getApplicantUserId() == applicantUserId)
				.orElseThrow(GroupMembershipService::applicationNotFound);
		if (!application.isPending()) {
			throw conflict("APPLICATION_ALREADY_DECIDED", "이미 처리된 참가 신청입니다.");
		}
		application.cancel();
	}

	@Transactional
	public void approve(long groupId, long applicationId, long leaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, leaderUserId);
		if (!group.isRecruiting()) {
			throw conflict("GROUP_RECRUITMENT_CLOSED", "모집이 마감된 모임입니다.");
		}
		GroupJoinApplication application = pendingApplication(groupId, applicationId);
		if (memberCount(group) >= group.getMaxMemberCount()) {
			throw conflict("GROUP_CAPACITY_FULL", "모임 정원이 가득 찼습니다.");
		}
		if (members.existsByGroup_IdAndUser_Id(groupId, application.getApplicantUserId())) {
			throw conflict("ALREADY_GROUP_MEMBER", "이미 모임에 참여 중입니다.");
		}
		members.saveAndFlush(new GroupMember(group, application.getApplicant()));
		application.approve();
	}

	@Transactional
	public void reject(long groupId, long applicationId, long leaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, leaderUserId);
		pendingApplication(groupId, applicationId).reject();
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> members(long groupId, long requesterUserId) {
		StudyGroup group = findGroup(groupId);
		requireMember(group, requesterUserId);
		List<MemberResponse> responses = new ArrayList<>(members.findByGroup_IdOrderByJoinedAtAscIdAsc(groupId).stream()
				.map(member -> MemberResponse.from(member, group.getLeaderUserId()))
				.toList());
		if (responses.stream().noneMatch(member -> member.userId() == group.getLeaderUserId())) {
			responses.add(0, new MemberResponse(group.getLeaderUserId(), GroupMemberRole.LEADER, group.getCreatedAt()));
		}
		return List.copyOf(responses);
	}

	@Transactional
	public void removeMember(long groupId, long memberUserId, long leaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, leaderUserId);
		if (group.getLeaderUserId() == memberUserId) {
			throw conflict("LEADER_CANNOT_BE_REMOVED", "리더는 강퇴할 수 없습니다.");
		}
		GroupMember member = members.findByGroup_IdAndUser_Id(groupId, memberUserId)
				.orElseThrow(GroupMembershipService::memberNotFound);
		members.delete(member);
	}

	@Transactional
	public void leave(long groupId, long userId) {
		StudyGroup group = lockedGroup(groupId);
		if (group.getLeaderUserId() == userId) {
			throw conflict("LEADER_MUST_TRANSFER_FIRST", "리더는 권한을 양도한 뒤 탈퇴할 수 있습니다.");
		}
		GroupMember member = members.findByGroup_IdAndUser_Id(groupId, userId)
				.orElseThrow(GroupMembershipService::memberNotFound);
		members.delete(member);
	}

	@Transactional
	public void transferLeadership(long groupId, long newLeaderUserId, long currentLeaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, currentLeaderUserId);
		if (currentLeaderUserId == newLeaderUserId) {
			throw conflict("ALREADY_GROUP_LEADER", "이미 모임 리더인 사용자입니다.");
		}
		if (!members.existsByGroup_IdAndUser_Id(groupId, newLeaderUserId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NEW_LEADER_NOT_MEMBER",
					"모임에 참여 중인 사용자에게만 리더 권한을 양도할 수 있습니다.");
		}
		if (!members.existsByGroup_IdAndUser_Id(groupId, currentLeaderUserId)) {
			members.saveAndFlush(new GroupMember(group, findUser(currentLeaderUserId)));
		}
		group.transferLeadership(findUser(newLeaderUserId));
	}

	@Transactional
	public void closeRecruitment(long groupId, long leaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, leaderUserId);
		group.closeRecruitment();
	}

	@Transactional
	public void reopenRecruitment(long groupId, long leaderUserId) {
		StudyGroup group = lockedGroup(groupId);
		requireLeader(group, leaderUserId);
		group.reopenRecruitment();
	}

	private GroupJoinApplication pendingApplication(long groupId, long applicationId) {
		GroupJoinApplication application = applications.findByIdForUpdate(applicationId)
				.filter(found -> found.getGroupId() == groupId)
				.orElseThrow(GroupMembershipService::applicationNotFound);
		if (!application.isPending()) {
			throw conflict("APPLICATION_ALREADY_DECIDED", "이미 처리된 참가 신청입니다.");
		}
		return application;
	}

	private StudyGroup findGroup(long groupId) {
		return groups.findById(groupId).orElseThrow(GroupMembershipService::groupNotFound);
	}

	private StudyGroup lockedGroup(long groupId) {
		return groups.findByIdForUpdate(groupId).orElseThrow(GroupMembershipService::groupNotFound);
	}

	private User findUser(long userId) {
		return users.findById(userId).orElseThrow(GroupMembershipService::authenticationRequired);
	}

	private void requireLeader(StudyGroup group, long userId) {
		if (group.getLeaderUserId() != userId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_LEADER_REQUIRED", "모임 리더만 할 수 있습니다.");
		}
	}

	private void requireMember(StudyGroup group, long userId) {
		if (!isMember(group, userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED", "모임 참여자만 조회할 수 있습니다.");
		}
	}

	private boolean isMember(StudyGroup group, long userId) {
		return group.getLeaderUserId() == userId
				|| members.existsByGroup_IdAndUser_Id(group.getId(), userId);
	}

	private boolean isCurrentMember(StudyGroup group, long userId, Set<Long> memberGroupIds) {
		return group.getLeaderUserId() == userId || memberGroupIds.contains(group.getId());
	}

	private long memberCount(StudyGroup group) {
		long count = members.countByGroup_Id(group.getId());
		return members.existsByGroup_IdAndUser_Id(group.getId(), group.getLeaderUserId()) ? count : count + 1;
	}

	private Map<Long, GroupMemberCount> memberCounts(List<Long> groupIds) {
		if (groupIds.isEmpty()) return Map.of();
		return members.countMembersByGroupIds(groupIds).stream()
				.collect(Collectors.toMap(GroupMemberCount::getGroupId, Function.identity()));
	}

	private long currentMemberCount(StudyGroup group, Map<Long, GroupMemberCount> counts) {
		GroupMemberCount count = counts.get(group.getId());
		if (count == null) return 1;
		return count.getStoredMemberCount() + (count.getLeaderRowCount() > 0 ? 0 : 1);
	}

	private Map<Long, Profile> leaderProfiles(List<GroupJoinApplication> found) {
		List<Long> leaderIds = found.stream()
				.map(application -> application.getGroup().getLeaderUserId())
				.distinct()
				.toList();
		if (leaderIds.isEmpty()) return Map.of();
		return profiles.findAllById(leaderIds).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
	}

	private void validatePage(int page, int size) {
		if (page < 0) {
			throw invalidRequest("page는 0 이상이어야 합니다.");
		}
		if (size < 1 || size > 100) {
			throw invalidRequest("size는 1~100이어야 합니다.");
		}
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	private static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private static ApiException groupNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임이 존재하지 않습니다.");
	}

	private static ApiException memberNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_MEMBER_NOT_FOUND", "모임 참여자를 찾을 수 없습니다.");
	}

	private static ApiException applicationNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_APPLICATION_NOT_FOUND", "참가 신청을 찾을 수 없습니다.");
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	public record ApplicationResponse(Long applicationId, Long groupId, Long applicantUserId,
			GroupJoinApplicationStatus status, Instant requestedAt, Instant decidedAt) {
		static ApplicationResponse from(GroupJoinApplication application) {
			return new ApplicationResponse(application.getId(), application.getGroupId(),
					application.getApplicantUserId(), application.getStatus(), application.getRequestedAt(),
					application.getDecidedAt());
		}
	}

	public record MyApplicationPageResponse(List<MyApplicationResponse> content, int page, int size,
			long totalElements, int totalPages, boolean first, boolean last) {}

	public record MyApplicationResponse(Long applicationId, GroupJoinApplicationStatus status,
			Instant requestedAt, Instant decidedAt, boolean isCurrentMember,
			ApplicationGroupSummary group) {
		static MyApplicationResponse from(GroupJoinApplication application, boolean isCurrentMember,
				long currentMemberCount, Profile leaderProfile) {
			StudyGroup group = application.getGroup();
			return new MyApplicationResponse(application.getId(), application.getStatus(),
					application.getRequestedAt(), application.getDecidedAt(), isCurrentMember,
					ApplicationGroupSummary.from(group, currentMemberCount, leaderProfile));
		}
	}

	public record ApplicationGroupSummary(Long groupId, String title, Long leaderUserId,
			@Schema(nullable = true, description = "리더 프로필 이름. 프로필이 없으면 null")
			String leaderName,
			@Schema(nullable = true, description = "리더 프로필 이미지 URL. 프로필 또는 이미지가 없으면 null")
			String leaderAvatarUrl,
			com.mju.mjuton.group.domain.GroupCategory category,
			com.mju.mjuton.group.domain.GroupStatus groupStatus,
			String meetingRule, String location, long currentMemberCount, int maxMemberCount) {
		static ApplicationGroupSummary from(StudyGroup group, long currentMemberCount, Profile leaderProfile) {
			String leaderName = leaderProfile == null ? null : leaderProfile.getName();
			String leaderAvatarUrl = leaderProfile == null ? null : leaderProfile.getAvatarUrl();
			return new ApplicationGroupSummary(group.getId(), group.getTitle(), group.getLeaderUserId(),
					leaderName, leaderAvatarUrl, group.getCategory(), group.getStatus(), group.getMeetingRule(),
					group.getLocation(), currentMemberCount, group.getMaxMemberCount());
		}
	}

	public record MemberResponse(Long userId, GroupMemberRole role, Instant joinedAt) {
		static MemberResponse from(GroupMember member, long leaderUserId) {
			GroupMemberRole role = member.getUserId() == leaderUserId
					? GroupMemberRole.LEADER : GroupMemberRole.MEMBER;
			return new MemberResponse(member.getUserId(), role, member.getJoinedAt());
		}
	}
}
