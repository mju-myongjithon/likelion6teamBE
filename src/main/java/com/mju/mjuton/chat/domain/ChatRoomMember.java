package com.mju.mjuton.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "chat_room_members", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
public class ChatRoomMember {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "member_id")
	private Long id;
	@Column(name = "room_id", nullable = false)
	private Long roomId;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(nullable = false, updatable = false)
	private Instant joinedAt;
	@Column(name = "last_read_message_id", nullable = false)
	private Long lastReadMessageId;

	protected ChatRoomMember() {}

	public ChatRoomMember(Long roomId, Long userId) {
		this.roomId = roomId;
		this.userId = userId;
		this.joinedAt = Instant.now();
		this.lastReadMessageId = 0L;
	}

	public Long getId() { return id; }
	public Long getRoomId() { return roomId; }
	public Long getUserId() { return userId; }
	public Long getLastReadMessageId() { return lastReadMessageId; }

	/** messageId > lastReadMessageId 인 것만 안 읽은 메시지로 취급한다. 역행은 무시한다. */
	public void markReadUpTo(Long messageId) {
		if (messageId != null && messageId > this.lastReadMessageId) {
			this.lastReadMessageId = messageId;
		}
	}
}
