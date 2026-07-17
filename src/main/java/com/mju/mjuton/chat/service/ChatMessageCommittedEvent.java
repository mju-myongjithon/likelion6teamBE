package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.dto.ChatMessageResponse;
import java.util.List;

public record ChatMessageCommittedEvent(ChatMessageResponse message, List<Long> recipientUserIds) {}
