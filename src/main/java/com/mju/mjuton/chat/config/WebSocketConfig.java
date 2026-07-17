package com.mju.mjuton.chat.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private final ChatChannelInterceptor channelInterceptor;
	private final ChatHandshakeInterceptor handshakeInterceptor;
	private final ChatHandshakeHandler handshakeHandler;
	private final ChatWebSocketSessionRegistry sessions;
	private final List<String> allowedOrigins;

	public WebSocketConfig(@Lazy ChatChannelInterceptor channelInterceptor,
			ChatHandshakeInterceptor handshakeInterceptor, ChatHandshakeHandler handshakeHandler,
			ChatWebSocketSessionRegistry sessions,
			@Value("${mjuton.cors.allowed-origins}") String allowedOrigins) {
		this.channelInterceptor = channelInterceptor;
		this.handshakeInterceptor = handshakeInterceptor;
		this.handshakeHandler = handshakeHandler;
		this.sessions = sessions;
		this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim)
				.filter(origin -> !origin.isEmpty()).toList();
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws-chat")
				.addInterceptors(handshakeInterceptor)
				.setHandshakeHandler(handshakeHandler)
				.setAllowedOrigins(allowedOrigins.toArray(String[]::new));
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/queue");
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(channelInterceptor);
	}

	@Override
	public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
		registration.addDecoratorFactory(new WebSocketHandlerDecoratorFactory() {
			@Override
			public WebSocketHandler decorate(WebSocketHandler handler) {
				return new WebSocketHandlerDecorator(handler) {
					@Override
					public void afterConnectionEstablished(WebSocketSession session) throws Exception {
						sessions.register(session);
						super.afterConnectionEstablished(session);
					}

					@Override
					public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
							throws Exception {
						sessions.unregister(session);
						super.afterConnectionClosed(session, closeStatus);
					}
				};
			}
		});
	}

	@Bean
	ServletListenerRegistrationBean<ChatWebSocketSessionRegistry> chatSessionListener() {
		return new ServletListenerRegistrationBean<>(sessions);
	}
}
