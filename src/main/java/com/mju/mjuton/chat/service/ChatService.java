package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.domain.ChatMessage;
import com.mju.mjuton.chat.domain.ChatReadState;
import com.mju.mjuton.chat.domain.ChatRoom;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.repository.ChatMessageRepository;
import com.mju.mjuton.chat.repository.ChatReadStateRepository;
import com.mju.mjuton.chat.repository.ChatRoomRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅방은 스터디 모임(StudyGroup)에 1:1로 매달린다(StudyGroup.chatRoomId).
 * 접근 권한의 진실원은 그룹 멤버십(GroupMember)이다 — 채팅방은 별도 멤버 명단을 두지 않는다.
 * 따라서 그룹 리더/멤버면 자동으로 채팅에 접근할 수 있고, 승인/강퇴 시 채팅 쪽 동기화가 필요 없다.
 */
@Service
public class ChatService {
	private static final int DEFAULT_PAGE_SIZE = 50;

	private final ChatRoomRepository rooms;
	private final ChatReadStateRepository readStates;
	private final ChatMessageRepository messages;
	private final ChatMessagePublisher publisher;
	private final StudyGroupRepository groups;
	private final GroupMemberRepository groupMembers;
	private final ChatReadStateInitializer readStateInitializer;

	public ChatService(ChatRoomRepository rooms, ChatReadStateRepository readStates,
			ChatMessageRepository messages, ChatMessagePublisher publisher,
			StudyGroupRepository groups, GroupMemberRepository groupMembers,
			ChatReadStateInitializer readStateInitializer) {
		this.rooms = rooms;
		this.readStates = readStates;
		this.messages = messages;
		this.publisher = publisher;
		this.groups = groups;
		this.groupMembers = groupMembers;
		this.readStateInitializer = readStateInitializer;
	}

	/**
	 * 채팅방 생성 — 그룹 생성 시 GroupService가 호출한다. 방은 마지막 메시지 캐시만 갖는 껍데기이며
	 * 제목은 소유 모임에서 파생한다. 멤버 명단은 두지 않는다(접근은 그룹 멤버십으로 판정).
	 * 생성된 방 id를 돌려주어 StudyGroup에 링크하게 한다.
	 */
	@Transactional
	public Long createRoom() {
		return rooms.saveAndFlush(new ChatRoom()).getId();
	}

	/** 모임 삭제 시 GroupService가 호출 — 방과 그 방의 메시지/읽음 상태를 함께 제거해 고아 데이터를 남기지 않는다. */
	@Transactional
	public void deleteRoom(Long roomId) {
		if (roomId == null) {
			return;
		}
		messages.deleteByRoomId(roomId);
		readStates.deleteByRoomId(roomId);
		rooms.findById(roomId).ifPresent(rooms::delete);
	}

	/**
	 * 로그인 직후 프런트가 subscribe할 방 목록 — 내가 리더/멤버인 그룹들의 채팅방을 요약해 내려준다.
	 * 방 조회와 방별 안 읽음 수를 각각 한 번의 배치 쿼리로 처리해 그룹 수만큼의 N+1을 피한다.
	 */
	@Transactional(readOnly = true)
	public List<ChatRoomSummaryResponse> getMyRooms(Long userId) {
		List<StudyGroup> myGroups = groups.findMyGroups(userId);
		List<Long> roomIds = myGroups.stream()
				.map(StudyGroup::getChatRoomId)
				.filter(Objects::nonNull)
				.toList();
		if (roomIds.isEmpty()) {
			return List.of();
		}
		Map<Long, ChatRoom> roomById = rooms.findAllById(roomIds).stream()
				.collect(Collectors.toMap(ChatRoom::getId, room -> room));
		Map<Long, Long> unreadByRoom = messages.countUnreadByRoomIds(roomIds, userId).stream()
				.collect(Collectors.toMap(ChatMessageRepository.RoomUnreadCount::getRoomId,
						ChatMessageRepository.RoomUnreadCount::getUnreadCount));

		List<ChatRoomSummaryResponse> summaries = new ArrayList<>();
		for (StudyGroup group : myGroups) {
			ChatRoom room = roomById.get(group.getChatRoomId());
			if (room == null) {
				continue; // 방이 아직 링크되지 않았거나(백필 이전) 정합성이 깨진 경우 건너뛴다.
			}
			long unread = unreadByRoom.getOrDefault(room.getId(), 0L);
			summaries.add(new ChatRoomSummaryResponse(room.getId(), group.getTitle(),
					room.getLastMessage(), room.getLastMessageAt(), unread));
		}
		return summaries;
	}

	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long roomId, Long userId, Long beforeId, Integer size) {
		requireAccess(roomId, userId);
		Pageable page = PageRequest.of(0, size == null ? DEFAULT_PAGE_SIZE : size);
		List<ChatMessage> found = beforeId == null
				? messages.findByRoomIdOrderByIdDesc(roomId, page)
				: messages.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, page);
		return found.stream().map(ChatMessageResponse::from).toList();
	}

	/**
	 * 메시지 전송 순서: 1) 접근 검증 2) 저장 3) 방 캐시(lastMessage) 갱신 4) 브로드캐스트.
	 * 반드시 DB 저장이 끝난 뒤에 publisher.publish를 호출한다.
	 */
	@Transactional
	public ChatMessageResponse sendMessage(Long roomId, Long senderId, String content) {
		requireAccess(roomId, senderId);
		Instant now = Instant.now();
		ChatMessage saved = messages.saveAndFlush(new ChatMessage(roomId, senderId, content, now));
		// 목록용 캐시는 방 엔티티를 로드하지 않고 바로 갱신한다. lastMessage 컬럼 길이에 맞춰 잘라 저장한다.
		rooms.updateLastMessage(roomId, preview(content), now);

		ChatMessageResponse response = ChatMessageResponse.from(saved);
		publisher.publish(roomId, response);
		return response;
	}

	/**
	 * 채팅방을 읽은 시점에 프런트가 호출 — 읽음 상태 행을 없으면 만들고 lastReadMessageId를 갱신한다.
	 * 최초 생성은 별도 트랜잭션(readStateInitializer)에 위임해, 동시 최초 읽기의 유니크 경합이
	 * 이 트랜잭션을 오염시키지 않게 한다. 생성이 보장된 뒤 항상 존재하는 행을 갱신한다.
	 */
	@Transactional
	public void markAsRead(Long roomId, Long userId, Long lastReadMessageId) {
		requireAccess(roomId, userId);
		ChatReadState state = readStates.findByRoomIdAndUserId(roomId, userId).orElse(null);
		if (state == null) {
			readStateInitializer.ensureExists(roomId, userId);
			state = readStates.findByRoomIdAndUserId(roomId, userId).orElseThrow();
		}
		state.markReadUpTo(lastReadMessageId);
		readStates.saveAndFlush(state);
	}

	/**
	 * ChannelInterceptor(SUBSCRIBE/SEND)와 REST 인가 체크의 진실원.
	 * 방이 속한 그룹의 리더이거나 GroupMember면 접근을 허용한다.
	 */
	@Transactional(readOnly = true)
	public boolean canAccess(Long roomId, Long userId) {
		if (roomId == null || userId == null) {
			return false;
		}
		return groups.findByChatRoomId(roomId)
				.map(group -> userId.equals(group.getLeaderUserId())
						|| groupMembers.existsByGroup_IdAndUser_Id(group.getId(), userId))
				.orElse(false);
	}

	/** lastMessage 컬럼 길이를 넘는 본문은 목록 캐시용으로 잘라 저장한다(전송/저장 본문 자체는 그대로다). */
	private static String preview(String content) {
		return content.length() > ChatRoom.LAST_MESSAGE_MAX
				? content.substring(0, ChatRoom.LAST_MESSAGE_MAX)
				: content;
	}

	private void requireAccess(Long roomId, Long userId) {
		if (!canAccess(roomId, userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER", "채팅방에 접근할 권한이 없습니다.");
		}
	}
}
