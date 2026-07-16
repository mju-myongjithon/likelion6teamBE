package com.mju.mjuton.recruitment.dto;

import com.mju.mjuton.recruitment.domain.Recruitment;
import com.mju.mjuton.recruitment.domain.RecruitmentStatus;
import java.time.Instant;

public record RecruitmentResponse(Long id, Long authorId, Long chatRoomId, String title, String description,
		int capacity, RecruitmentStatus status, Instant createdAt) {
	public static RecruitmentResponse from(Recruitment recruitment) {
		return new RecruitmentResponse(recruitment.getId(), recruitment.getAuthorId(), recruitment.getChatRoomId(),
				recruitment.getTitle(), recruitment.getDescription(), recruitment.getCapacity(),
				recruitment.getStatus(), recruitment.getCreatedAt());
	}
}
