package com.mju.mjuton.chat.config;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.chat.service.ChatService;
import java.util.Map;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP 프레임 단위 인가 체크.
 * - CONNECT: 세션에 로그인된 userId가 없으면 연결 자체를 거부한다.
 * - SUBSCRIBE(/topic/chat/{roomId}), SEND(/app/chat/{roomId}/messages): 해당 방의 멤버가 아니면 거부한다.
 * HttpSessionHandshakeInterceptor가 핸드셰이크 시점에 HttpSession의 attribute를
 * 이 STOMP 세션의 sessionAttributes로 복사해주므로, AuthController와 동일한 세션 값을 그대로 읽을 수 있다.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {
	private final ChatService chatService;

	public ChatChannelInterceptor(ChatService chatService) {
		this.chatService = chatService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
		StompCommand command = accessor.getCommand();
		if (command == null) return message;

		Long userId = currentUserId(accessor);

		if (command == StompCommand.CONNECT && userId == null) {
			throw new MessagingException("로그인이 필요합니다.");
		}
		if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
			Long roomId = extractRoomId(accessor.getDestination());
			if (roomId != null && !chatService.canAccess(roomId, userId)) {
				throw new MessagingException("채팅방에 접근할 권한이 없습니다.");
			}
		}
		return message;
	}

	private Long currentUserId(StompHeaderAccessor accessor) {
		Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
		if (sessionAttributes == null) return null;
		Object value = sessionAttributes.get(AuthController.SESSION_USER_ID);
		return value instanceof Long userId ? userId : null;
	}

	/** "/topic/chat/{roomId}" 또는 "/app/chat/{roomId}/messages"에서 roomId를 뽑아낸다. */
	private Long extractRoomId(String destination) {
		if (destination == null) return null;
		String[] parts = destination.split("/");
		for (int i = 0; i < parts.length - 1; i++) {
			if (parts[i].equals("chat")) {
				try {
					return Long.parseLong(parts[i + 1]);
				} catch (NumberFormatException ignored) {
					return null;
				}
			}
		}
		return null;
	}
}
