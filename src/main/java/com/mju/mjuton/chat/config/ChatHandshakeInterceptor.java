package com.mju.mjuton.chat.config;

import com.mju.mjuton.auth.controller.AuthController;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
	static final String HTTP_SESSION_ID = "chatHttpSessionId";
	static final String USER_ID = "chatUserId";

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Map<String, Object> attributes) {
		if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
		HttpSession session = servletRequest.getServletRequest().getSession(false);
		if (session == null
				|| !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			return false;
		}
		attributes.put(HTTP_SESSION_ID, session.getId());
		attributes.put(USER_ID, userId);
		return true;
	}

	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
			WebSocketHandler wsHandler, Exception exception) {}
}
