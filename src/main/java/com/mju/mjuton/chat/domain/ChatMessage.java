package com.mju.mjuton.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Long id;
	@Column(name = "room_id", nullable = false)
	private Long roomId;
	@Column(name = "sender_id", nullable = false)
	private Long senderId;
	@Column(nullable = false, length = 2000)
	private String content;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatMessage() {}

	public ChatMessage(Long roomId, Long senderId, String content, Instant now) {
		this.roomId = roomId;
		this.senderId = senderId;
		this.content = content;
		this.createdAt = now;
	}

	public Long getId() { return id; }
	public Long getRoomId() { return roomId; }
	public Long getSenderId() { return senderId; }
	public String getContent() { return content; }
	public Instant getCreatedAt() { return createdAt; }
}
