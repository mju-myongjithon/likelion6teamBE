package com.mju.mjuton.chat.dto;

import java.time.Instant;

public record ChatRoomSummaryResponse(Long groupId, String title, long memberCount,
		Long lastMessageId, String lastMessage, Instant lastMessageAt, long unreadCount) {}
