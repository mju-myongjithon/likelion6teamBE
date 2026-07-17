package com.mju.mjuton.chat.config;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ChatWebSocketSessionRegistry implements HttpSessionListener {
	private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

	void register(WebSocketSession session) {
		Object httpSessionId = session.getAttributes().get(ChatHandshakeInterceptor.HTTP_SESSION_ID);
		if (httpSessionId instanceof String id) {
			sessions.computeIfAbsent(id, ignored -> ConcurrentHashMap.newKeySet()).add(session);
		}
	}

	void unregister(WebSocketSession session) {
		Object httpSessionId = session.getAttributes().get(ChatHandshakeInterceptor.HTTP_SESSION_ID);
		if (!(httpSessionId instanceof String id)) return;
		Set<WebSocketSession> found = sessions.get(id);
		if (found == null) return;
		found.remove(session);
		if (found.isEmpty()) sessions.remove(id, found);
	}

	public void closeByHttpSessionId(String httpSessionId) {
		Set<WebSocketSession> found = sessions.remove(httpSessionId);
		if (found == null) return;
		for (WebSocketSession session : found) {
			try {
				if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
			} catch (IOException ignored) {
				// 이미 전송 계층에서 종료 중인 세션은 제거만 한다.
			}
		}
	}

	@Override
	public void sessionDestroyed(HttpSessionEvent event) {
		closeByHttpSessionId(event.getSession().getId());
	}
}
