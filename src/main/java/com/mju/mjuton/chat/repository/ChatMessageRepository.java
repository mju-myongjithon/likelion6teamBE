package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	/** 최초 입장 시 최신 메시지부터 페이지 단위로 조회. */
	List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
	/** 스크롤 위로 올릴 때 특정 메시지 이전 것만 페이지 단위로 조회. */
	List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId, Pageable pageable);
	/** lastReadMessageId보다 큰 것 = 안 읽은 메시지. */
	long countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);
}
