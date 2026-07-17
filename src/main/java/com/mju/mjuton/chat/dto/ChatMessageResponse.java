package com.mju.mjuton.chat.dto;

import com.mju.mjuton.chat.domain.ChatMessage;
import java.time.Instant;

public record ChatMessageResponse(Long messageId, Long groupId, Long senderId,
		String senderName, String content, Instant createdAt) {
	public static ChatMessageResponse from(ChatMessage message, String senderName) {
		return new ChatMessageResponse(message.getId(), message.getGroupId(), message.getSenderId(),
				senderName, message.getContent(), message.getCreatedAt());
	}
}
