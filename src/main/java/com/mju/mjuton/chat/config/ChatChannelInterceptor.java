package com.mju.mjuton.chat.config;

import com.mju.mjuton.chat.service.ChatService;
import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class ChatChannelInterceptor implements ChannelInterceptor {
	private static final Pattern SEND =
			Pattern.compile("^/app/chat/groups/([1-9][0-9]*)/messages$");
	private static final Pattern SUBSCRIBE =
			Pattern.compile("^/user/queue/chat/groups/([1-9][0-9]*)$");

	private final ChatService chatService;

	public ChatChannelInterceptor(ChatService chatService) {
		this.chatService = chatService;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
		StompCommand command = accessor.getCommand();
		if (command == null) return message;
		Principal principal = accessor.getUser();
		if (principal == null) throw new MessagingException("로그인이 필요합니다.");
		long userId = parseUserId(principal);
		if (command == StompCommand.CONNECT || command == StompCommand.DISCONNECT
				|| command == StompCommand.UNSUBSCRIBE) {
			return message;
		}
		Pattern allowed = command == StompCommand.SEND ? SEND
				: command == StompCommand.SUBSCRIBE ? SUBSCRIBE : null;
		if (allowed == null) throw new MessagingException("허용되지 않은 STOMP 명령입니다.");
		Matcher matcher = allowed.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
		if (!matcher.matches()) throw new MessagingException("허용되지 않은 STOMP 목적지입니다.");
		long groupId;
		try {
			groupId = Long.parseLong(matcher.group(1));
		} catch (NumberFormatException exception) {
			throw new MessagingException("유효하지 않은 모임 id입니다.", exception);
		}
		if (!chatService.canAccess(groupId, userId)) {
			throw new MessagingException("모임 채팅 접근 권한이 없습니다.");
		}
		return message;
	}

	private long parseUserId(Principal principal) {
		try {
			return Long.parseLong(principal.getName());
		} catch (NumberFormatException exception) {
			throw new MessagingException("유효하지 않은 사용자입니다.", exception);
		}
	}
}
