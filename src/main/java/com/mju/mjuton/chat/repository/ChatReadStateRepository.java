package com.mju.mjuton.chat.repository;

import com.mju.mjuton.chat.domain.ChatReadState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatReadStateRepository extends JpaRepository<ChatReadState, Long> {
	Optional<ChatReadState> findByGroup_IdAndUser_Id(Long groupId, Long userId);
}
