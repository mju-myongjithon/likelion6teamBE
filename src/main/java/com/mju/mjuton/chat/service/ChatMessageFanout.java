package com.mju.mjuton.chat.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ChatMessageFanout {
	private final SimpMessagingTemplate messaging;

	public ChatMessageFanout(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ChatMessageCommittedEvent event) {
		String destination = "/queue/chat/groups/" + event.message().groupId();
		for (Long userId : event.recipientUserIds()) {
			messaging.convertAndSendToUser(userId.toString(), destination, event.message());
		}
	}
}
