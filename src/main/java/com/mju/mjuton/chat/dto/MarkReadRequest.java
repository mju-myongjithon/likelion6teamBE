package com.mju.mjuton.chat.dto;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(
		@NotNull(message = "마지막으로 읽은 메시지 id는 필수입니다.")
		Long lastReadMessageId) {
}
