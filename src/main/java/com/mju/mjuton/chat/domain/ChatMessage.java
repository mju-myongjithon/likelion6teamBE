package com.mju.mjuton.chat.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.group.domain.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "chat_messages", indexes = {
		@Index(name = "idx_chat_messages_group_message", columnList = "group_id, message_id")
})
public class ChatMessage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "message_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "sender_user_id", nullable = false)
	private User sender;
	@Column(nullable = false, length = 2000)
	private String content;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected ChatMessage() {}

	public ChatMessage(StudyGroup group, User sender, String content) {
		this.group = group;
		this.sender = sender;
		this.content = content;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public Long getSenderId() { return sender.getId(); }
	public String getContent() { return content; }
	public Instant getCreatedAt() { return createdAt; }
}
