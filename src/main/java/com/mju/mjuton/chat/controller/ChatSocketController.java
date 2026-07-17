package com.mju.mjuton.chat.controller;

import com.mju.mjuton.chat.dto.SendMessageRequest;
import com.mju.mjuton.chat.service.ChatService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class ChatSocketController {
	private final ChatService chatService;

	public ChatSocketController(ChatService chatService) {
		this.chatService = chatService;
	}

	@MessageMapping("/chat/groups/{groupId}/messages")
	public void send(@DestinationVariable long groupId, @Valid SendMessageRequest request,
			Principal principal) {
		chatService.send(groupId, Long.parseLong(principal.getName()), request.content());
	}
}
