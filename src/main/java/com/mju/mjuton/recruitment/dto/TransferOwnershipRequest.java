package com.mju.mjuton.recruitment.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
		@NotNull(message = "새 방장의 사용자 id는 필수입니다.")
		Long newOwnerId) {
}
