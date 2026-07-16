package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.domain.ChatMessage;
import com.mju.mjuton.chat.domain.ChatRoom;
import com.mju.mjuton.chat.domain.ChatRoomMember;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.repository.ChatMessageRepository;
import com.mju.mjuton.chat.repository.ChatRoomMemberRepository;
import com.mju.mjuton.chat.repository.ChatRoomRepository;
import com.mju.mjuton.global.ApiException;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
	private static final int DEFAULT_PAGE_SIZE = 50;

	private final ChatRoomRepository rooms;
	private final ChatRoomMemberRepository members;
	private final ChatMessageRepository messages;
	private final ChatMessagePublisher publisher;

	public ChatService(ChatRoomRepository rooms, ChatRoomMemberRepository members,
			ChatMessageRepository messages, ChatMessagePublisher publisher) {
		this.rooms = rooms;
		this.members = members;
		this.messages = messages;
		this.publisher = publisher;
	}

	/**
	 * 채팅방 생성 — 개발/테스트용 최소 API. 생성자를 곧바로 멤버로 등록해
	 * 이후 목록 조회/구독/전송이 바로 가능하도록 한다.
	 */
	@Transactional
	public ChatRoomSummaryResponse createRoom(Long creatorId, String title) {
		ChatRoom room = rooms.saveAndFlush(new ChatRoom(title));
		members.saveAndFlush(new ChatRoomMember(room.getId(), creatorId));
		return new ChatRoomSummaryResponse(room.getId(), room.getTitle(),
				room.getLastMessage(), room.getLastMessageAt(), 0L);
	}

	/** 멤버 초대 — 기존 패턴대로 요청자가 먼저 방 멤버여야 하고, 중복 초대는 막는다. */
	@Transactional
	public void addMember(Long roomId, Long requesterId, Long targetUserId) {
		requireMember(roomId, requesterId);
		if (members.existsByRoomIdAndUserId(roomId, targetUserId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ROOM_MEMBER", "이미 채팅방 멤버입니다.");
		}
		members.saveAndFlush(new ChatRoomMember(roomId, targetUserId));
	}

	/**
	 * 멤버 제거(방 나가기)용 원시 기능. 명단(ChatRoomMember)에서 한 명을 삭제한다.
	 * "방장은 나갈 수 없다" 같은 정책은 chat이 알지 못하므로 호출하는 쪽(recruitment)에서 판단한다.
	 */
	@Transactional
	public void removeMember(Long roomId, Long userId) {
		ChatRoomMember member = members.findByRoomIdAndUserId(roomId, userId)
				.orElseThrow(this::notMember);
		members.delete(member);
	}

	/** 로그인 직후 프런트가 subscribe할 방 목록 + 목록 화면에 필요한 요약 정보. */
	@Transactional(readOnly = true)
	public List<ChatRoomSummaryResponse> getMyRooms(Long userId) {
		return members.findByUserId(userId).stream()
				.map(member -> toSummary(member, findRoom(member.getRoomId())))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ChatMessageResponse> getMessages(Long roomId, Long userId, Long beforeId, Integer size) {
		requireMember(roomId, userId);
		Pageable page = PageRequest.of(0, size == null ? DEFAULT_PAGE_SIZE : size);
		List<ChatMessage> found = beforeId == null
				? messages.findByRoomIdOrderByIdDesc(roomId, page)
				: messages.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, beforeId, page);
		return found.stream().map(ChatMessageResponse::from).toList();
	}

	/**
	 * 메시지 전송 순서: 1) 멤버 검증 2) 저장 3) 방 캐시(lastMessage) 갱신 4) 브로드캐스트.
	 * 반드시 DB 저장이 끝난 뒤에 publisher.publish를 호출한다.
	 */
	@Transactional
	public ChatMessageResponse sendMessage(Long roomId, Long senderId, String content) {
		requireMember(roomId, senderId);
		Instant now = Instant.now();
		ChatMessage saved = messages.saveAndFlush(new ChatMessage(roomId, senderId, content, now));
		findRoom(roomId).updateLastMessage(content, now);

		ChatMessageResponse response = ChatMessageResponse.from(saved);
		publisher.publish(roomId, response);
		return response;
	}

	/** 채팅방을 나가거나 특정 시점에 프런트가 호출 — lastReadMessageId를 갱신한다. */
	@Transactional
	public void markAsRead(Long roomId, Long userId, Long lastReadMessageId) {
		ChatRoomMember member = members.findByRoomIdAndUserId(roomId, userId)
				.orElseThrow(this::notMember);
		member.markReadUpTo(lastReadMessageId);
	}

	/** ChannelInterceptor에서 SUBSCRIBE/SEND 시점의 인가 체크에 사용한다. */
	@Transactional(readOnly = true)
	public boolean isMember(Long roomId, Long userId) {
		return roomId != null && userId != null && members.existsByRoomIdAndUserId(roomId, userId);
	}

	/** 방의 현재 멤버 수 — recruitment 등 외부 모듈이 정원 검사에 사용한다. */
	@Transactional(readOnly = true)
	public long countMembers(Long roomId) {
		return members.countByRoomId(roomId);
	}

	private ChatRoomSummaryResponse toSummary(ChatRoomMember member, ChatRoom room) {
		long unread = messages.countByRoomIdAndIdGreaterThan(room.getId(), member.getLastReadMessageId());
		return new ChatRoomSummaryResponse(room.getId(), room.getTitle(), room.getLastMessage(),
				room.getLastMessageAt(), unread);
	}

	private ChatRoom findRoom(Long roomId) {
		return rooms.findById(roomId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "채팅방을 찾을 수 없습니다."));
	}

	private void requireMember(Long roomId, Long userId) {
		if (!members.existsByRoomIdAndUserId(roomId, userId)) {
			throw notMember();
		}
	}

	private ApiException notMember() {
		return new ApiException(HttpStatus.FORBIDDEN, "NOT_ROOM_MEMBER", "채팅방 멤버가 아닙니다.");
	}
}
