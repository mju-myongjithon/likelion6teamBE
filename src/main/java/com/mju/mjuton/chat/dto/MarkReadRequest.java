package com.mju.mjuton.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkReadRequest(
		@NotNull(message = "messageId는 필수입니다.")
		@Positive(message = "messageId는 양수여야 합니다.")
		Long messageId) {}
