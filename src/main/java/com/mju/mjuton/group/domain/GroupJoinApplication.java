package com.mju.mjuton.group.domain;

import com.mju.mjuton.auth.domain.User;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "group_join_applications",
		uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "applicant_user_id"}))
public class GroupJoinApplication {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_join_application_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "applicant_user_id", nullable = false)
	private User applicant;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupJoinApplicationStatus status;
	@Column(nullable = false)
	private Instant requestedAt;
	@Column
	private Instant decidedAt;

	protected GroupJoinApplication() {}

	public GroupJoinApplication(StudyGroup group, User applicant) {
		this.group = group;
		this.applicant = applicant;
		this.status = GroupJoinApplicationStatus.PENDING;
		this.requestedAt = Instant.now();
	}

	public void reapply() {
		this.status = GroupJoinApplicationStatus.PENDING;
		this.requestedAt = Instant.now();
		this.decidedAt = null;
	}

	public void approve() {
		this.status = GroupJoinApplicationStatus.APPROVED;
		this.decidedAt = Instant.now();
	}

	public void reject() {
		this.status = GroupJoinApplicationStatus.REJECTED;
		this.decidedAt = Instant.now();
	}

	public boolean isPending() { return status == GroupJoinApplicationStatus.PENDING; }
	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public Long getApplicantUserId() { return applicant.getId(); }
	public User getApplicant() { return applicant; }
	public GroupJoinApplicationStatus getStatus() { return status; }
	public Instant getRequestedAt() { return requestedAt; }
	public Instant getDecidedAt() { return decidedAt; }
}
