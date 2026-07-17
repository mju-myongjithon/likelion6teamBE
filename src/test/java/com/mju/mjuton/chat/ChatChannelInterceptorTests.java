package com.mju.mjuton.chat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mju.mjuton.chat.config.ChatChannelInterceptor;
import com.mju.mjuton.chat.service.ChatService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class ChatChannelInterceptorTests {
	private final ChatService chatService = mock(ChatService.class);
	private final ChatChannelInterceptor interceptor = new ChatChannelInterceptor(chatService);
	private final MessageChannel channel = mock(MessageChannel.class);
	private final Principal user = () -> "7";

	@Test
	void exactSendAndSubscribeDestinationsAreAllowedForCurrentMember() {
		when(chatService.canAccess(12L, 7L)).thenReturn(true);
		assertThatCode(() -> interceptor.preSend(
				frame(StompCommand.SEND, "/app/chat/groups/12/messages"), channel)).doesNotThrowAnyException();
		assertThatCode(() -> interceptor.preSend(
				frame(StompCommand.SUBSCRIBE, "/user/queue/chat/groups/12"), channel)).doesNotThrowAnyException();
	}

	@Test
	void brokerSendMalformedDestinationAndRevokedMembershipFailClosed() {
		when(chatService.canAccess(12L, 7L)).thenReturn(false);
		assertThatThrownBy(() -> interceptor.preSend(
				frame(StompCommand.SEND, "/queue/chat/groups/12"), channel))
				.isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> interceptor.preSend(
				frame(StompCommand.SUBSCRIBE, "/user/queue/chat/groups/12/extra"), channel))
				.isInstanceOf(MessagingException.class);
		assertThatThrownBy(() -> interceptor.preSend(
				frame(StompCommand.SEND, "/app/chat/groups/12/messages"), channel))
				.isInstanceOf(MessagingException.class);
	}

	private Message<byte[]> frame(StompCommand command, String destination) {
		StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
		accessor.setDestination(destination);
		accessor.setUser(user);
		return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
	}
}
