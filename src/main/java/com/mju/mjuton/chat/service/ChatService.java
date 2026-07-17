package com.mju.mjuton.chat.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.domain.ChatMessage;
import com.mju.mjuton.chat.domain.ChatReadState;
import com.mju.mjuton.chat.dto.ChatHistoryResponse;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.repository.ChatMessageRepository;
import com.mju.mjuton.chat.repository.ChatMessageRepository.GroupUnreadCount;
import com.mju.mjuton.chat.repository.ChatReadStateRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository.GroupMemberCount;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
	private static final int DEFAULT_PAGE_SIZE = 50;
	private static final int MAX_PAGE_SIZE = 100;
	private static final String UNKNOWN_SENDER_NAME = "알 수 없는 사용자";

	private final ChatMessageRepository messages;
	private final ChatReadStateRepository readStates;
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final UserRepository users;
	private final ProfileRepository profiles;
	private final ApplicationEventPublisher events;

	public ChatService(ChatMessageRepository messages, ChatReadStateRepository readStates,
			StudyGroupRepository groups, GroupMemberRepository members, UserRepository users,
			ProfileRepository profiles, ApplicationEventPublisher events) {
		this.messages = messages;
		this.readStates = readStates;
		this.groups = groups;
		this.members = members;
		this.users = users;
		this.profiles = profiles;
		this.events = events;
	}

	@Transactional(readOnly = true)
	public List<ChatRoomSummaryResponse> rooms(long userId) {
		findUser(userId);
		List<StudyGroup> myGroups = groups.findMyGroups(userId);
		if (myGroups.isEmpty()) return List.of();
		List<Long> groupIds = myGroups.stream().map(StudyGroup::getId).toList();
		Map<Long, ChatMessage> latest = messages.findLatestByGroupIds(groupIds).stream()
				.collect(Collectors.toMap(ChatMessage::getGroupId, Function.identity()));
		Map<Long, Long> unread = messages.countUnreadByGroupIds(groupIds, userId).stream()
				.collect(Collectors.toMap(GroupUnreadCount::getGroupId, GroupUnreadCount::getUnreadCount));
		Map<Long, GroupMemberCount> counts = members.countMembersByGroupIds(groupIds).stream()
				.collect(Collectors.toMap(GroupMemberCount::getGroupId, Function.identity()));
		return myGroups.stream()
				.map(group -> roomSummary(group, latest.get(group.getId()),
						unread.getOrDefault(group.getId(), 0L), counts.get(group.getId())))
				.sorted(Comparator.comparing(ChatRoomSummaryResponse::lastMessageAt,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(ChatRoomSummaryResponse::groupId, Comparator.reverseOrder()))
				.toList();
	}

	@Transactional
	public ChatHistoryResponse messages(long groupId, long userId, Long before, Integer requestedSize) {
		StudyGroup group = readLockedGroup(groupId);
		requireMember(group, userId);
		if (before != null && before < 1) throw invalidRequest("before는 양수여야 합니다.");
		int size = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
		if (size < 1 || size > MAX_PAGE_SIZE) throw invalidRequest("size는 1~100이어야 합니다.");
		PageRequest page = PageRequest.of(0, size + 1);
		List<ChatMessage> found = before == null
				? messages.findByGroup_IdOrderByIdDesc(groupId, page)
				: messages.findByGroup_IdAndIdLessThanOrderByIdDesc(groupId, before, page);
		boolean hasNext = found.size() > size;
		List<ChatMessage> pageMessages = hasNext ? found.subList(0, size) : found;
		Map<Long, Profile> profileByUserId = profiles.findAllById(
						pageMessages.stream().map(ChatMessage::getSenderId).distinct().toList()).stream()
				.collect(Collectors.toMap(Profile::getUserId, Function.identity()));
		List<ChatMessageResponse> response = pageMessages.stream()
				.map(message -> ChatMessageResponse.from(message,
						senderName(message, profileByUserId.get(message.getSenderId()))))
				.toList();
		Long nextCursor = hasNext ? pageMessages.get(pageMessages.size() - 1).getId() : null;
		return new ChatHistoryResponse(response, nextCursor, hasNext);
	}

	@Transactional
	public ChatMessageResponse send(long groupId, long senderId, String content) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, senderId);
		String normalized = normalize(content);
		User sender = findUser(senderId);
		ChatMessage saved = messages.saveAndFlush(new ChatMessage(group, sender, normalized));
		ChatReadState senderState = readStates.findByGroup_IdAndUser_Id(groupId, senderId)
				.orElseGet(() -> new ChatReadState(group, sender));
		senderState.markReadUpTo(saved.getId());
		readStates.saveAndFlush(senderState);
		Profile profile = profiles.findById(senderId).orElse(null);
		ChatMessageResponse response = ChatMessageResponse.from(saved, senderName(saved, profile));
		Set<Long> recipients = new LinkedHashSet<>(members.findUserIdsByGroupId(groupId));
		recipients.add(group.getLeaderUserId());
		events.publishEvent(new ChatMessageCommittedEvent(response, List.copyOf(recipients)));
		return response;
	}

	@Transactional
	public void markRead(long groupId, long userId, long messageId) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		if (!messages.existsByIdAndGroup_Id(messageId, groupId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_MESSAGE_NOT_FOUND",
					"해당 모임의 메시지가 존재하지 않습니다.");
		}
		User user = findUser(userId);
		ChatReadState state = readStates.findByGroup_IdAndUser_Id(groupId, userId)
				.orElseGet(() -> new ChatReadState(group, user));
		state.markReadUpTo(messageId);
		readStates.saveAndFlush(state);
	}

	@Transactional(readOnly = true)
	public boolean canAccess(long groupId, Long userId) {
		if (userId == null) return false;
		return groups.findById(groupId)
				.map(group -> isMember(group, userId))
				.orElse(false);
	}

	private ChatRoomSummaryResponse roomSummary(StudyGroup group, ChatMessage latest,
			long unreadCount, GroupMemberCount count) {
		long memberCount = count == null ? 1
				: count.getStoredMemberCount() + (count.getLeaderRowCount() > 0 ? 0 : 1);
		return new ChatRoomSummaryResponse(group.getId(), group.getTitle(), memberCount,
				latest == null ? null : latest.getId(),
				latest == null ? null : latest.getContent(),
				latest == null ? null : latest.getCreatedAt(), unreadCount);
	}

	private StudyGroup lockedGroup(long groupId) {
		return groups.findByIdForUpdate(groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
						"모임이 존재하지 않습니다."));
	}

	private StudyGroup readLockedGroup(long groupId) {
		return groups.findByIdForRead(groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
						"모임이 존재하지 않습니다."));
	}

	private User findUser(long userId) {
		return users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
						"로그인이 필요합니다."));
	}

	private void requireMember(StudyGroup group, long userId) {
		if (!isMember(group, userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED",
					"모임 참여자만 채팅에 접근할 수 있습니다.");
		}
	}

	private boolean isMember(StudyGroup group, long userId) {
		return group.getLeaderUserId() == userId
				|| members.existsByGroup_IdAndUser_Id(group.getId(), userId);
	}

	private String normalize(String content) {
		if (content == null) throw invalidRequest("메시지 내용은 필수입니다.");
		String normalized = content.trim();
		if (normalized.isEmpty() || normalized.length() > 2000) {
			throw invalidRequest("메시지는 1~2000자여야 합니다.");
		}
		return normalized;
	}

	private String senderName(ChatMessage message, Profile profile) {
		return profile == null ? UNKNOWN_SENDER_NAME : profile.getName();
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}
}
