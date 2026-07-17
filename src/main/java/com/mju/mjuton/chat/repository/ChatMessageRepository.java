package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	@EntityGraph(attributePaths = "sender")
	List<ChatMessage> findByGroup_IdOrderByIdDesc(Long groupId, Pageable pageable);

	@EntityGraph(attributePaths = "sender")
	List<ChatMessage> findByGroup_IdAndIdLessThanOrderByIdDesc(
			Long groupId, Long before, Pageable pageable);

	boolean existsByIdAndGroup_Id(Long messageId, Long groupId);

	@Query("""
			select message
			from ChatMessage message
			join fetch message.sender
			where message.group.id in :groupIds
			  and message.id = (
			      select max(latest.id) from ChatMessage latest
			      where latest.group.id = message.group.id
			  )
			""")
	List<ChatMessage> findLatestByGroupIds(@Param("groupIds") List<Long> groupIds);

	@Query("""
			select message.group.id as groupId, count(message.id) as unreadCount
			from ChatMessage message
			where message.group.id in :groupIds
			  and message.sender.id <> :userId
			  and message.id > coalesce((
			      select state.lastReadMessageId from ChatReadState state
			      where state.group.id = message.group.id and state.user.id = :userId
			  ), 0L)
			group by message.group.id
			""")
	List<GroupUnreadCount> countUnreadByGroupIds(
			@Param("groupIds") List<Long> groupIds, @Param("userId") Long userId);

	interface GroupUnreadCount {
		Long getGroupId();
		long getUnreadCount();
	}
}
