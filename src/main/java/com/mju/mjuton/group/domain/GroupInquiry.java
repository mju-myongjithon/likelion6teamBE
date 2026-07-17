package com.mju.mjuton.group.domain;

import com.mju.mjuton.auth.domain.User;
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
@Table(name = "group_inquiries", indexes = {
		@Index(name = "idx_group_inquiries_group_created",
				columnList = "group_id, created_at, group_inquiry_id")
})
public class GroupInquiry {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_inquiry_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User author;
	@Column(nullable = false, length = 1000)
	private String content;
	@Column(length = 1000)
	private String answerContent;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	private Instant answeredAt;

	protected GroupInquiry() {}

	public GroupInquiry(StudyGroup group, User author, String content) {
		this.group = group;
		this.author = author;
		this.content = content;
		this.createdAt = Instant.now();
	}

	public void answer(String answerContent) {
		this.answerContent = answerContent;
		this.answeredAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public Long getGroupLeaderUserId() { return group.getLeaderUserId(); }
	public Long getAuthorUserId() { return author.getId(); }
	public String getContent() { return content; }
	public String getAnswerContent() { return answerContent; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getAnsweredAt() { return answeredAt; }
	public boolean isAnswered() { return answerContent != null; }
}
