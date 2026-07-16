package com.mju.mjuton.chat.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.chat.dto.SendMessageRequest;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * 클라이언트가 "/app/chat/{roomId}/messages"로 보낸 메시지를 처리한다.
 * 브로드캐스트는 @SendTo가 아니라 ChatService -> ChatMessagePublisher가 담당한다.
 * (저장 이후에만 브로드캐스트가 나가도록 순서를 강제하고, 나중에 Redis로 바꿀 때 이 컨트롤러를 건드리지 않기 위함.)
 */
@Controller
public class ChatSocketController {
	private final ChatService chatService;

	public ChatSocketController(ChatService chatService) {
		this.chatService = chatService;
	}

	@MessageMapping("/chat/{roomId}/messages")
	public void send(@DestinationVariable Long roomId, @Valid SendMessageRequest request,
			SimpMessageHeaderAccessor accessor) {
		Long userId = currentUserId(accessor);
		chatService.sendMessage(roomId, userId, request.content());
	}

	private Long currentUserId(SimpMessageHeaderAccessor accessor) {
		Object value = accessor.getSessionAttributes() == null ? null
				: accessor.getSessionAttributes().get(AuthController.SESSION_USER_ID);
		if (!(value instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
