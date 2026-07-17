package com.mju.mjuton.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 채팅방. 제목은 소유 모임(StudyGroup)에서 파생하므로 여기 두지 않는다.
 * 목록 화면용 마지막 메시지 캐시(lastMessage/lastMessageAt)만 보관한다.
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
	/** lastMessage 컬럼 길이. 메시지 본문(최대 2000자)이 이보다 길면 잘라 저장한다. */
	public static final int LAST_MESSAGE_MAX = 500;

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "room_id")
	private Long id;
	@Column(name = "last_message", length = LAST_MESSAGE_MAX)
	private String lastMessage;
	@Column(name = "last_message_at")
	private Instant lastMessageAt;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public ChatRoom() {
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getLastMessage() { return lastMessage; }
	public Instant getLastMessageAt() { return lastMessageAt; }
}
