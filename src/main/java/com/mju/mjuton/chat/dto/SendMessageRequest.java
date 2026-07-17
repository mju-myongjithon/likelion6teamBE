package com.mju.mjuton.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
		@NotBlank(message = "메시지 내용은 필수입니다.")
		@Size(max = 2000, message = "메시지는 최대 2000자까지 입력할 수 있습니다.")
		String content) {}
