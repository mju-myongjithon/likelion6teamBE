package com.mju.mjuton.chat.dto;

import java.time.Instant;

/** 채팅 목록(좌측 리스트) 한 줄에 필요한 정보 — lastMessage/lastMessageAt은 ChatRoom의 캐시 필드를 그대로 내려준다. */
public record ChatRoomSummaryResponse(Long roomId, String title, String lastMessage,
		Instant lastMessageAt, long unreadCount) {
}
