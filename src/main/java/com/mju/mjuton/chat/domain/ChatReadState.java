package com.mju.mjuton.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 방-사용자별 읽음 상태. 채팅방 "접근 권한"은 더 이상 여기서 판단하지 않는다(그룹 멤버십이 진실원).
 * 오직 "이 사용자가 이 방에서 어디까지 읽었는가"(lastReadMessageId)만 기록해 안 읽은 개수 계산에 쓴다.
 * 행은 사용자가 방을 처음 읽는 시점(markAsRead)에 lazy하게 생성된다.
 */
@Entity
@Table(name = "chat_read_states",
		uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
public class ChatReadState {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "read_state_id")
	private Long id;
	@Column(name = "room_id", nullable = false)
	private Long roomId;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(name = "last_read_message_id", nullable = false)
	private Long lastReadMessageId;

	protected ChatReadState() {}

	public ChatReadState(Long roomId, Long userId) {
		this.roomId = roomId;
		this.userId = userId;
		this.lastReadMessageId = 0L;
	}

	public Long getLastReadMessageId() { return lastReadMessageId; }

	/** messageId > lastReadMessageId 인 것만 안 읽은 메시지로 취급한다. 역행은 무시한다. */
	public void markReadUpTo(Long messageId) {
		if (messageId != null && messageId > this.lastReadMessageId) {
			this.lastReadMessageId = messageId;
		}
	}
}
