package com.mju.mjuton.chat.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class ChatWebSocketSessionRegistryTests {
	@Test
	void explicitLogoutClosesEverySocketForHttpSession() throws Exception {
		ChatWebSocketSessionRegistry registry = new ChatWebSocketSessionRegistry();
		WebSocketSession first = socket("http-session");
		WebSocketSession second = socket("http-session");
		registry.register(first);
		registry.register(second);

		registry.closeByHttpSessionId("http-session");

		verify(first).close(CloseStatus.POLICY_VIOLATION);
		verify(second).close(CloseStatus.POLICY_VIOLATION);
	}

	@Test
	void httpSessionTimeoutClosesRegisteredSocket() throws Exception {
		ChatWebSocketSessionRegistry registry = new ChatWebSocketSessionRegistry();
		WebSocketSession socket = socket("expired-session");
		registry.register(socket);
		HttpSession httpSession = mock(HttpSession.class);
		when(httpSession.getId()).thenReturn("expired-session");

		registry.sessionDestroyed(new HttpSessionEvent(httpSession));

		verify(socket).close(CloseStatus.POLICY_VIOLATION);
	}

	private WebSocketSession socket(String httpSessionId) {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getAttributes()).thenReturn(
				Map.of(ChatHandshakeInterceptor.HTTP_SESSION_ID, httpSessionId));
		when(session.isOpen()).thenReturn(true);
		return session;
	}
}
