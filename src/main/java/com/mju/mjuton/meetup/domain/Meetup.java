package com.mju.mjuton.meetup.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.group.domain.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "meetups")
public class Meetup {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "meetup_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creator_user_id", nullable = false)
	private User creator;
	@Column(nullable = false, length = 100)
	private String name;
	@Column(nullable = false)
	private LocalDate meetingDate;
	@Column(nullable = false)
	private LocalTime meetingTime;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MeetupStatus status;
	private Long confirmedOptionId;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant updatedAt;

	protected Meetup() {}

	public Meetup(StudyGroup group, User creator, String name, LocalDate meetingDate, LocalTime meetingTime) {
		this.group = group;
		this.creator = creator;
		this.name = name;
		this.meetingDate = meetingDate;
		this.meetingTime = meetingTime;
		this.status = MeetupStatus.OPEN;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void confirm(long optionId) {
		this.status = MeetupStatus.CONFIRMED;
		this.confirmedOptionId = optionId;
		this.updatedAt = Instant.now();
	}

	public void cancel() {
		this.status = MeetupStatus.CANCELLED;
		this.updatedAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public Long getGroupLeaderUserId() { return group.getLeaderUserId(); }
	public Long getCreatorUserId() { return creator.getId(); }
	public String getName() { return name; }
	public LocalDate getMeetingDate() { return meetingDate; }
	public LocalTime getMeetingTime() { return meetingTime; }
	public MeetupStatus getStatus() { return status; }
	public Long getConfirmedOptionId() { return confirmedOptionId; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
