package com.mju.mjuton.chat.dto;

import java.util.List;

public record ChatHistoryResponse(List<ChatMessageResponse> messages, Long nextCursor, boolean hasNext) {}
