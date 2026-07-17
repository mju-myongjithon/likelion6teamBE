package com.mju.mjuton.group.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupMemberRole;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.domain.StudyGroup.RoleValues;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository.GroupMemberCount;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final UserRepository users;
	private final ChatService chatService;

	public GroupService(StudyGroupRepository groups, GroupMemberRepository members, UserRepository users,
			ChatService chatService) {
		this.groups = groups;
		this.members = members;
		this.users = users;
		this.chatService = chatService;
	}

	@Transactional
	public GroupDetail create(long userId, GroupValues values) {
		User leader = users.findById(userId).orElseThrow(GroupService::authenticationRequired);
		NormalizedValues normalized = normalize(values);
		StudyGroup group = new StudyGroup(leader, normalized.title(), normalized.description(),
				normalized.maxMemberCount(), normalized.meetingRule(), normalized.location());
		group.replaceRoles(normalized.recruitingRoles());
		group.addInitialMember(leader);
		// 모임 전용 채팅방을 함께 만들어 링크한다. 리더는 GroupMember이므로 채팅 접근은 자동으로 열린다.
		group.linkChatRoom(chatService.createRoom());
		StudyGroup saved = groups.saveAndFlush(group);
		return GroupDetail.from(saved, 1);
	}

	@Transactional(readOnly = true)
	public List<GroupSummary> findAll() {
		List<StudyGroup> found = groups.findAllByOrderByCreatedAtDescIdDesc();
		Map<Long, GroupMemberCount> counts = memberCounts(found);
		return found.stream()
				.map(group -> GroupSummary.from(group, currentMemberCount(group, counts)))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<MyGroupResponse> findMine(long userId) {
		ensureUserExists(userId);
		List<StudyGroup> found = groups.findMyGroups(userId);
		Map<Long, GroupMemberCount> counts = memberCounts(found);
		return found.stream()
				.map(group -> MyGroupResponse.from(group, userId, currentMemberCount(group, counts)))
				.toList();
	}

	@Transactional(readOnly = true)
	public GroupDetail find(long groupId) {
		StudyGroup group = findGroup(groupId);
		return GroupDetail.from(group, currentMemberCount(group));
	}

	@Transactional
	public GroupDetail update(long userId, long groupId, GroupValues values) {
		ensureUserExists(userId);
		StudyGroup group = findGroup(groupId);
		ensureLeader(group, userId);
		NormalizedValues normalized = normalize(values);
		long memberCount = currentMemberCount(group);
		if (normalized.maxMemberCount() < memberCount) {
			throw invalidRequest("총 정원은 현재 참여자 수보다 작을 수 없습니다.");
		}
		group.update(normalized.title(), normalized.description(), normalized.maxMemberCount(),
				normalized.meetingRule(), normalized.location(), normalized.recruitingRoles());
		StudyGroup saved = groups.saveAndFlush(group);
		return GroupDetail.from(saved, memberCount);
	}

	@Transactional
	public void delete(long userId, long groupId) {
		ensureUserExists(userId);
		StudyGroup group = findGroup(groupId);
		ensureLeader(group, userId);
		Long chatRoomId = group.getChatRoomId();
		groups.delete(group);
		// 모임에 매달린 채팅방과 그 메시지/읽음 상태도 함께 정리한다(chatRoomId는 FK가 아니라 cascade 안 됨).
		chatService.deleteRoom(chatRoomId);
	}

	private StudyGroup findGroup(long groupId) {
		return groups.findWithRecruitingRolesById(groupId).orElseThrow(GroupService::groupNotFound);
	}

	private void ensureUserExists(long userId) {
		if (!users.existsById(userId)) throw authenticationRequired();
	}

	private void ensureLeader(StudyGroup group, long userId) {
		if (group.getLeaderUserId() != userId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_FORBIDDEN", "모임 리더만 변경할 수 있습니다.");
		}
	}

	private long currentMemberCount(StudyGroup group) {
		return currentMemberCount(group, memberCounts(List.of(group)));
	}

	private long currentMemberCount(StudyGroup group, Map<Long, GroupMemberCount> counts) {
		GroupMemberCount count = counts.get(group.getId());
		if (count == null) return 1;
		return count.getStoredMemberCount() + (count.getLeaderRowCount() > 0 ? 0 : 1);
	}

	private Map<Long, GroupMemberCount> memberCounts(List<StudyGroup> found) {
		if (found.isEmpty()) return Map.of();
		List<Long> groupIds = found.stream().map(StudyGroup::getId).toList();
		return members.countMembersByGroupIds(groupIds).stream()
				.collect(Collectors.toMap(GroupMemberCount::getGroupId, Function.identity()));
	}

	private NormalizedValues normalize(GroupValues values) {
		if (values == null) throw invalidRequest("요청 본문은 필수입니다.");
		String title = required(values.title(), "모임 제목", 100);
		String description = required(values.description(), "모임 소개", 2000);
		if (values.maxMemberCount() == null || values.maxMemberCount() < 1 || values.maxMemberCount() > 100) {
			throw invalidRequest("총 정원은 1~100명이어야 합니다.");
		}
		String meetingRule = required(values.meetingRule(), "모임 규칙", 1000);
		String location = required(values.location(), "모임 장소", 200);
		List<RoleValues> roles = roles(values.recruitingRoles());
		return new NormalizedValues(title, description, values.maxMemberCount(), meetingRule, location, roles);
	}

	private List<RoleValues> roles(List<RoleValues> values) {
		if (values == null) throw invalidRequest("모집 역할 배열은 필수입니다.");
		if (values.size() > 20) throw invalidRequest("모집 역할은 최대 20개까지 입력할 수 있습니다.");
		List<RoleValues> normalized = new ArrayList<>();
		Set<RoleValues> unique = new HashSet<>();
		for (RoleValues value : values) {
			if (value == null) throw invalidRequest("모집 역할은 null일 수 없습니다.");
			RoleValues role = new RoleValues(required(value.role(), "모집 역할", 50),
					optional(value.skill(), "모집 기술", 100));
			if (!unique.add(role)) throw invalidRequest("동일한 모집 역할과 기술을 중복해 입력할 수 없습니다.");
			normalized.add(role);
		}
		return normalized;
	}

	private String required(String value, String field, int maxLength) {
		if (value == null) throw invalidRequest(field + "은(는) 필수입니다.");
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw invalidRequest(field + "은(는) 1~" + maxLength + "자여야 합니다.");
		}
		return normalized;
	}

	private String optional(String value, String field, int maxLength) {
		if (value == null) return null;
		return required(value, field, maxLength);
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException groupNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임이 존재하지 않습니다.");
	}

	public record GroupValues(String title, String description, Integer maxMemberCount, String meetingRule,
			String location, List<RoleValues> recruitingRoles) {}

	private record NormalizedValues(String title, String description, int maxMemberCount, String meetingRule,
			String location, List<RoleValues> recruitingRoles) {}

	public record GroupSummary(Long groupId, String title, com.mju.mjuton.group.domain.GroupCategory category,
			com.mju.mjuton.group.domain.GroupStatus status, String location, String meetingRule,
			int maxMemberCount, long currentMemberCount, java.time.Instant createdAt) {
		static GroupSummary from(StudyGroup group, long currentMemberCount) {
			return new GroupSummary(group.getId(), group.getTitle(), group.getCategory(), group.getStatus(),
					group.getLocation(), group.getMeetingRule(), group.getMaxMemberCount(), currentMemberCount,
					group.getCreatedAt());
		}
	}

	public record MyGroupResponse(Long groupId, String title,
			com.mju.mjuton.group.domain.GroupCategory category,
			com.mju.mjuton.group.domain.GroupStatus status, String location, String meetingRule,
			int maxMemberCount, long currentMemberCount, GroupMemberRole role, java.time.Instant createdAt) {
		static MyGroupResponse from(StudyGroup group, long userId, long currentMemberCount) {
			GroupMemberRole role = group.getLeaderUserId() == userId
					? GroupMemberRole.LEADER : GroupMemberRole.MEMBER;
			return new MyGroupResponse(group.getId(), group.getTitle(), group.getCategory(), group.getStatus(),
					group.getLocation(), group.getMeetingRule(), group.getMaxMemberCount(), currentMemberCount,
					role, group.getCreatedAt());
		}
	}

	public record RoleDetail(String role, String skill) {}

	public record GroupDetail(Long groupId, Long leaderUserId, String title,
			com.mju.mjuton.group.domain.GroupCategory category, com.mju.mjuton.group.domain.GroupStatus status,
			String description, int maxMemberCount, String meetingRule, String location,
			long currentMemberCount, List<RoleDetail> recruitingRoles,
			java.time.Instant createdAt, java.time.Instant updatedAt) {
		static GroupDetail from(StudyGroup group, long currentMemberCount) {
			return new GroupDetail(group.getId(), group.getLeaderUserId(), group.getTitle(), group.getCategory(),
					group.getStatus(), group.getDescription(), group.getMaxMemberCount(), group.getMeetingRule(),
					group.getLocation(), currentMemberCount, group.getRecruitingRoles().stream()
							.map(role -> new RoleDetail(role.getRole(), role.getSkill())).toList(),
					group.getCreatedAt(), group.getUpdatedAt());
		}
	}
}
