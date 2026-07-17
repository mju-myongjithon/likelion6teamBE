package com.mju.mjuton.chat.config;

import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채팅 기능 도입 이전에 이미 만들어진 모임들에는 채팅방이 없다. 부팅 시 chatRoomId가 비어 있는
 * 모임을 찾아 채팅방을 만들어 링크한다. 링크된 모임은 다음 부팅부터 대상에서 빠지므로 멱등하다.
 */
@Component
public class ChatRoomBackfill implements CommandLineRunner {
	private static final Logger log = LoggerFactory.getLogger(ChatRoomBackfill.class);

	private final StudyGroupRepository groups;
	private final ChatService chatService;

	public ChatRoomBackfill(StudyGroupRepository groups, ChatService chatService) {
		this.groups = groups;
		this.chatService = chatService;
	}

	@Override
	@Transactional
	public void run(String... args) {
		List<StudyGroup> pending = groups.findByChatRoomIdIsNull();
		if (pending.isEmpty()) {
			return;
		}
		for (StudyGroup group : pending) {
			group.linkChatRoom(chatService.createRoom());
		}
		log.info("[chat] 채팅방 백필 완료: {}개 모임에 채팅방을 연결했습니다.", pending.size());
	}
}
