package com.mju.mjuton.chat.config;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.domain.ChatRoom;
import com.mju.mjuton.chat.domain.ChatRoomMember;
import com.mju.mjuton.chat.repository.ChatRoomMemberRepository;
import com.mju.mjuton.chat.repository.ChatRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * dev 프로필 전용 시드 데이터. 회원가입/인증코드/초대 과정을 건너뛰고 바로 실시간 채팅을
 * 시험해볼 수 있도록, 두 명의 테스트 사용자와 그들이 함께 속한 채팅방 하나를 만들어 둔다.
 * 운영(prod)에서는 로드되지 않는다.
 */
@Component
@Profile("dev")
public class ChatDevSeeder implements CommandLineRunner {
	private static final Logger log = LoggerFactory.getLogger(ChatDevSeeder.class);
	private static final String PASSWORD = "password123";

	private final UserRepository users;
	private final ChatRoomRepository rooms;
	private final ChatRoomMemberRepository members;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public ChatDevSeeder(UserRepository users, ChatRoomRepository rooms, ChatRoomMemberRepository members) {
		this.users = users;
		this.rooms = rooms;
		this.members = members;
	}

	@Override
	public void run(String... args) {
		User alice = createUser("alice@mju.ac.kr");
		User bob = createUser("bob@mju.ac.kr");
		ChatRoom room = rooms.saveAndFlush(new ChatRoom("테스트 채팅방"));
		members.saveAndFlush(new ChatRoomMember(room.getId(), alice.getId()));
		members.saveAndFlush(new ChatRoomMember(room.getId(), bob.getId()));

		log.info("========================================================");
		log.info(" [DEV] 채팅 테스트 준비 완료");
		log.info(" 테스트 페이지 : http://localhost:8080/chat-test.html");
		log.info(" 사용자 1      : alice@mju.ac.kr / {} (userId={})", PASSWORD, alice.getId());
		log.info(" 사용자 2      : bob@mju.ac.kr   / {} (userId={})", PASSWORD, bob.getId());
		log.info(" 공용 채팅방 id : {}", room.getId());
		log.info("========================================================");
	}

	private User createUser(String email) {
		return users.findByEmail(email)
				.orElseGet(() -> users.saveAndFlush(new User(email, passwordEncoder.encode(PASSWORD))));
	}
}
