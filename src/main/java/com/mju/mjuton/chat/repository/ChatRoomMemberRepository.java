package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatRoomMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {
	boolean existsByRoomIdAndUserId(Long roomId, Long userId);
	Optional<ChatRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);
	/** 정원 검사 등에서 쓰는 방의 현재 멤버 수. */
	long countByRoomId(Long roomId);
	/** 로그인 시 사용자가 가입한 모든 채팅방을 조회 — 프런트에서 이 목록을 전부 subscribe한다. */
	List<ChatRoomMember> findByUserId(Long userId);
}
