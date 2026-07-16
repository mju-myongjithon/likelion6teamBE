package com.mju.mjuton.chat.dto;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
		@NotNull(message = "초대할 사용자 id는 필수입니다.")
		Long userId) {
}
