package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.dto.ChatMessageResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 단일 서버 기준 구현체. STOMP 내장 브로커로 /topic/chat/{roomId} 구독자에게 바로 전달한다.
 * 향후 서버가 여러 대가 되면 이 클래스 대신
 *  - RedisChatMessagePublisher(발행: Redis channel로 publish)
 *  - RedisChatMessageSubscriber(구독: Redis 메시지를 받아 이 SimpMessagingTemplate으로 다시 broadcast)
 * 조합으로 교체한다. ChatService, Controller는 변경하지 않는다.
 */
@Component
public class LocalChatMessagePublisher implements ChatMessagePublisher {
	private final SimpMessagingTemplate messagingTemplate;

	public LocalChatMessagePublisher(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void publish(Long roomId, ChatMessageResponse message) {
		messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
	}
}
