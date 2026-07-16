package com.mju.mjuton.recruitment.dto;

import com.mju.mjuton.recruitment.domain.JoinRequest;
import com.mju.mjuton.recruitment.domain.JoinRequestStatus;
import java.time.Instant;

public record JoinRequestResponse(Long id, Long recruitmentId, Long applicantId, JoinRequestStatus status,
		Instant createdAt) {
	public static JoinRequestResponse from(JoinRequest request) {
		return new JoinRequestResponse(request.getId(), request.getRecruitmentId(), request.getApplicantId(),
				request.getStatus(), request.getCreatedAt());
	}
}
