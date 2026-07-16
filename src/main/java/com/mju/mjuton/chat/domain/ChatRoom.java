package com.mju.mjuton.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "room_id")
	private Long id;
	@Column(nullable = false, length = 100)
	private String title;
	@Column(name = "last_message", length = 500)
	private String lastMessage;
	@Column(name = "last_message_at")
	private Instant lastMessageAt;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatRoom() {}

	public ChatRoom(String title) {
		this.title = title;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public String getTitle() { return title; }
	public String getLastMessage() { return lastMessage; }
	public Instant getLastMessageAt() { return lastMessageAt; }

	/** 메시지 전송 시 목록 화면용 캐시(마지막 메시지)를 갱신한다. */
	public void updateLastMessage(String content, Instant sentAt) {
		this.lastMessage = content;
		this.lastMessageAt = sentAt;
	}
}
