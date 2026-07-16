package com.mju.mjuton.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRoomRequest(
		@NotBlank(message = "채팅방 제목은 필수입니다.")
		@Size(max = 100, message = "채팅방 제목은 100자를 넘을 수 없습니다.")
		String title) {
}
