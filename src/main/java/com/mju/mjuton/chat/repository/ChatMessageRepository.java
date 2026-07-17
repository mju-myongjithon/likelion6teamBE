package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	/** 최초 입장 시 최신 메시지부터 페이지 단위로 조회. */
	List<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);
	/** 스크롤 위로 올릴 때 특정 메시지 이전 것만 페이지 단위로 조회. */
	List<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long beforeId, Pageable pageable);
	/** lastReadMessageId보다 큰 것 = 안 읽은 메시지. */
	long countByRoomIdAndIdGreaterThan(Long roomId, Long messageId);

	/**
	 * 채팅 목록용 방별 안 읽은 메시지 수를 한 번에 계산한다(N+1 회피).
	 * 각 방의 사용자별 lastReadMessageId(없으면 0)보다 id가 큰 메시지를 방별로 센다.
	 * 안 읽은 메시지가 0인 방은 결과에 나오지 않으므로, 호출부에서 0으로 기본 처리한다.
	 */
	@Query("""
			select message.roomId as roomId, count(message.id) as unreadCount
			from ChatMessage message
			where message.roomId in :roomIds
			  and message.id > coalesce(
			      (select state.lastReadMessageId from ChatReadState state
			       where state.roomId = message.roomId and state.userId = :userId), 0L)
			group by message.roomId
			""")
	List<RoomUnreadCount> countUnreadByRoomIds(@Param("roomIds") List<Long> roomIds, @Param("userId") Long userId);

	/** 모임(=채팅방) 삭제 시 그 방의 메시지를 한 번에 제거한다. */
	@Modifying
	@Query("delete from ChatMessage message where message.roomId = :roomId")
	void deleteByRoomId(@Param("roomId") Long roomId);

	interface RoomUnreadCount {
		Long getRoomId();
		long getUnreadCount();
	}
}
