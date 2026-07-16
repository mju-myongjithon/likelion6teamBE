package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.dto.ChatMessageResponse;

/**
 * 저장된 메시지를 실시간 구독자에게 전달하는 역할을 추상화한다.
 * 지금은 단일 서버 기준 {@link LocalChatMessagePublisher}(SimpMessagingTemplate 직접 호출)만 있다.
 * 서버가 여러 대로 늘어나면 이 인터페이스의 구현체를 Redis Pub/Sub 기반으로 교체하면 되고,
 * ChatService/Controller/React 쪽 코드는 전혀 손댈 필요가 없다.
 */
public interface ChatMessagePublisher {
	void publish(Long roomId, ChatMessageResponse message);
}
