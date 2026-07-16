package com.mju.mjuton.recruitment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRecruitmentRequest(
		@NotBlank(message = "모집글 제목은 필수입니다.")
		@Size(max = 100, message = "모집글 제목은 100자를 넘을 수 없습니다.")
		String title,
		@NotBlank(message = "모집글 설명은 필수입니다.")
		@Size(max = 2000, message = "모집글 설명은 2000자를 넘을 수 없습니다.")
		String description,
		@Min(value = 2, message = "정원은 방장 포함 2명 이상이어야 합니다.")
		@Max(value = 100, message = "정원은 100명을 넘을 수 없습니다.")
		int capacity) {
}
