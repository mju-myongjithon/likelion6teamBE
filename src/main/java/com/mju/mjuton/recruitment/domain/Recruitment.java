package com.mju.mjuton.recruitment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 팀원 모집글. 하나의 모집글은 하나의 채팅방(chatRoomId)을 가지며, 작성자(authorId)가 방장 겸 승인권자다.
 * 참가신청을 승인하면 신청자가 이 모집글의 채팅방 멤버로 승격된다.
 */
@Entity
@Table(name = "recruitments")
public class Recruitment {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recruitment_id")
	private Long id;
	@Column(name = "author_id", nullable = false)
	private Long authorId;
	@Column(name = "chat_room_id", nullable = false)
	private Long chatRoomId;
	@Column(nullable = false, length = 100)
	private String title;
	@Column(nullable = false, length = 2000)
	private String description;
	/** 정원(방장 포함). 채팅방 멤버 수가 이 값에 도달하면 더 승인할 수 없다. */
	@Column(nullable = false)
	private int capacity;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RecruitmentStatus status;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Recruitment() {}

	public Recruitment(Long authorId, Long chatRoomId, String title, String description, int capacity) {
		this.authorId = authorId;
		this.chatRoomId = chatRoomId;
		this.title = title;
		this.description = description;
		this.capacity = capacity;
		this.status = RecruitmentStatus.RECRUITING;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getAuthorId() { return authorId; }
	public Long getChatRoomId() { return chatRoomId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public int getCapacity() { return capacity; }
	public RecruitmentStatus getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }

	public boolean isAuthor(Long userId) { return authorId.equals(userId); }
	public boolean isRecruiting() { return status == RecruitmentStatus.RECRUITING; }

	/** 방장이 모집을 마감한다. */
	public void close() { this.status = RecruitmentStatus.CLOSED; }

	/** 방장을 다른 사용자에게 양도한다. authorId는 항상 실제 채팅방 멤버를 가리켜야 한다. */
	public void transferAuthorTo(Long newAuthorId) { this.authorId = newAuthorId; }
}
