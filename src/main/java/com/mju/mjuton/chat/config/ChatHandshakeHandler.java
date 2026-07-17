package com.mju.mjuton.chat.config;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

@Component
public class ChatHandshakeHandler extends DefaultHandshakeHandler {
	@Override
	protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		Object userId = attributes.get(ChatHandshakeInterceptor.USER_ID);
		return userId instanceof Long id ? () -> id.toString() : null;
	}
}
