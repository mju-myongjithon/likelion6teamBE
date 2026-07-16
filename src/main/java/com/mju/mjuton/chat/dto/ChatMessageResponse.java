package com.mju.mjuton.chat.dto;

import com.mju.mjuton.chat.domain.ChatMessage;
import java.time.Instant;

public record ChatMessageResponse(Long id, Long roomId, Long senderId, String content, Instant createdAt) {
	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(message.getId(), message.getRoomId(), message.getSenderId(),
				message.getContent(), message.getCreatedAt());
	}
}
