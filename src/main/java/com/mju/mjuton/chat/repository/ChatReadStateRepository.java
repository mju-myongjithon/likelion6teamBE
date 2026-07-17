package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatReadState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
	Optional<ChatReadState> findByRoomIdAndUserId(Long roomId, Long userId);

	/** 모임(=채팅방) 삭제 시 그 방의 읽음 상태를 한 번에 제거한다. */
	@Modifying
	@Query("delete from ChatReadState state where state.roomId = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);
}
