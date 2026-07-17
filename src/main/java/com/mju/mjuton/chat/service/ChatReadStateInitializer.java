package com.mju.mjuton.chat.service;

import com.mju.mjuton.chat.domain.ChatReadState;
import com.mju.mjuton.chat.repository.ChatReadStateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 읽음 상태 행의 최초 생성을 별도 트랜잭션(REQUIRES_NEW)에서 처리한다.
 * 유니크 제약(room_id,user_id) 위반은 호출한 바깥 트랜잭션을 오염시키기 때문에,
 * 동시 최초 읽기 경합은 여기 독립 트랜잭션 안에서 삼켜 바깥 트랜잭션이 계속 진행되게 한다.
 */
@Component
public class ChatReadStateInitializer {
	private final ChatReadStateRepository readStates;

	public ChatReadStateInitializer(ChatReadStateRepository readStates) {
		this.readStates = readStates;
	}

	/** 행이 없으면 초기 행을 만든다. 다른 트랜잭션이 먼저 만들어 유니크 위반이 나면 이미 존재하는 것이므로 무시한다. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureExists(Long roomId, Long userId) {
		if (readStates.findByRoomIdAndUserId(roomId, userId).isPresent()) {
			return;
		}
		try {
			readStates.saveAndFlush(new ChatReadState(roomId, userId));
		} catch (DataIntegrityViolationException concurrentCreate) {
			// 동시 최초 읽기로 다른 트랜잭션이 먼저 행을 만든 경우 — 정상 경합이므로 무시한다.
		}
	}
}
