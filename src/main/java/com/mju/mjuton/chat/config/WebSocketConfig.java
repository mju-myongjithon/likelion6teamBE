package com.mju.mjuton.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	private final ChatChannelInterceptor chatChannelInterceptor;
	/** 핸드셰이크를 허용할 origin 목록. 기본값은 React 개발서버이며, dev 프로필에서 로컬 테스트 페이지 origin을 추가한다. */
	private final String[] allowedOrigins;

	/*
	 * @Lazy로 주입해 순환 참조를 끊는다.
	 * WebSocketConfig는 STOMP 브로커 인프라(SimpMessagingTemplate 등)를 구성하는데,
	 * ChatChannelInterceptor -> ChatService -> LocalChatMessagePublisher -> SimpMessagingTemplate로
	 * 그 인프라에 다시 의존한다. 지연 주입으로 인터셉터(및 하위 서비스 그래프)는
	 * 컨텍스트 기동이 끝난 뒤 첫 STOMP 프레임 처리 시점에 실체화된다.
	 */
	public WebSocketConfig(@Lazy ChatChannelInterceptor chatChannelInterceptor,
			@Value("${chat.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
		this.chatChannelInterceptor = chatChannelInterceptor;
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws-chat")
				// AuthController가 채운 HttpSession attribute(userId 등)를 STOMP 세션으로 그대로 복사한다.
				.addInterceptors(new HttpSessionHandshakeInterceptor())
				.setAllowedOriginPatterns(allowedOrigins)
				.withSockJS();
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic"); // 서버 -> 클라이언트 broadcast prefix
		registry.setApplicationDestinationPrefixes("/app"); // 클라이언트 -> 서버 전송 prefix
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(chatChannelInterceptor);
	}
}
