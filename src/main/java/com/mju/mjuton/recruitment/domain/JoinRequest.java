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
 * 모집글에 대한 참가신청. 승인/거절 전까지 PENDING이며, 방장의 결정으로 APPROVED/REJECTED가 된다.
 * 거절 후에는 새 JoinRequest로 자유롭게 재신청할 수 있다(중복된 PENDING만 막는다).
 */
@Entity
@Table(name = "join_requests")
public class JoinRequest {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "join_request_id")
	private Long id;
	@Column(name = "recruitment_id", nullable = false)
	private Long recruitmentId;
	@Column(name = "applicant_id", nullable = false)
	private Long applicantId;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JoinRequestStatus status;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "decided_at")
	private Instant decidedAt;

	protected JoinRequest() {}

	public JoinRequest(Long recruitmentId, Long applicantId) {
		this.recruitmentId = recruitmentId;
		this.applicantId = applicantId;
		this.status = JoinRequestStatus.PENDING;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getRecruitmentId() { return recruitmentId; }
	public Long getApplicantId() { return applicantId; }
	public JoinRequestStatus getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getDecidedAt() { return decidedAt; }

	public boolean isPending() { return status == JoinRequestStatus.PENDING; }

	public void approve(Instant now) {
		this.status = JoinRequestStatus.APPROVED;
		this.decidedAt = now;
	}

	public void reject(Instant now) {
		this.status = JoinRequestStatus.REJECTED;
		this.decidedAt = now;
	}
}
