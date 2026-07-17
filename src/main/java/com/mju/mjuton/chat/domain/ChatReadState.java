package com.mju.mjuton.chat.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.group.domain.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "chat_read_states",
		uniqueConstraints = @UniqueConstraint(name = "uk_chat_read_states_group_user",
				columnNames = {"group_id", "user_id"}))
public class ChatReadState {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "read_state_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;
	@Column(nullable = false)
	private Long lastReadMessageId;

	protected ChatReadState() {}

	public ChatReadState(StudyGroup group, User user) {
		this.group = group;
		this.user = user;
		this.lastReadMessageId = 0L;
	}

	public void markReadUpTo(long messageId) {
		if (messageId > lastReadMessageId) lastReadMessageId = messageId;
	}
}
