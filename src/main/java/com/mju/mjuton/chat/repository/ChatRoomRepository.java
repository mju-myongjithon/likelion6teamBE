package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatRoom;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {
	/** 메시지 전송 시 목록 화면용 캐시(마지막 메시지)를 방 엔티티 로드 없이 바로 갱신한다. */
	@Modifying
	@Query("update ChatRoom room set room.lastMessage = :content, room.lastMessageAt = :sentAt where room.id = :roomId")
	void updateLastMessage(@Param("roomId") Long roomId, @Param("content") String content,
			@Param("sentAt") Instant sentAt);
}
