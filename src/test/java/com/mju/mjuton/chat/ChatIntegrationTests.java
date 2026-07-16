package com.mju.mjuton.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.service.ChatMessagePublisher;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ChatIntegrationTests.PublisherTestConfig.class)
class ChatIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ChatService chatService;
	@Autowired CapturingPublisher publisher;

	@BeforeEach
	void resetPublisher() {
		publisher.lastRoomId = null;
		publisher.lastMessage = null;
	}

	@Test
	void myRoomsReturnsCreatedRooms() throws Exception {
		MockHttpSession session = sessionFor("rooms-owner@mju.ac.kr");

		mvc.perform(post("/api/chat/rooms").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"백엔드 스터디\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.roomId").isNumber())
				.andExpect(jsonPath("$.title").value("백엔드 스터디"))
				.andExpect(jsonPath("$.unreadCount").value(0));
		mvc.perform(post("/api/chat/rooms").session(session).contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"프론트 스터디\"}"))
				.andExpect(status().isCreated());

		mvc.perform(get("/api/chat/rooms").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void nonMemberCannotSendOrInvite() throws Exception {
		long ownerId = user("send-owner@mju.ac.kr").getId();
		long outsiderId = user("send-outsider@mju.ac.kr").getId();
		long roomId = chatService.createRoom(ownerId, "비공개 방").roomId();

		// 서비스 계층: 멤버가 아닌 사용자의 전송은 거부된다.
		assertThatThrownBy(() -> chatService.sendMessage(roomId, outsiderId, "몰래 보내기"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getCode()).isEqualTo("NOT_ROOM_MEMBER");
				});
		assertThat(publisher.lastMessage).isNull(); // 거부된 전송은 브로드캐스트되지 않는다.

		// REST 초대 API도 요청자가 멤버가 아니면 거부된다.
		MockHttpSession outsiderSession = new MockHttpSession();
		outsiderSession.setAttribute(AuthController.SESSION_USER_ID, outsiderId);
		mvc.perform(post("/api/chat/rooms/" + roomId + "/members").session(outsiderSession)
				.contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + ownerId + "}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
	}

	@Test
	void sendBroadcastsAfterSaveAndMarkReadClearsUnread() throws Exception {
		long ownerId = user("flow-owner@mju.ac.kr").getId();
		long memberId = user("flow-member@mju.ac.kr").getId();
		long roomId = chatService.createRoom(ownerId, "협업 방").roomId();
		chatService.addMember(roomId, ownerId, memberId);

		ChatMessageResponse sent = chatService.sendMessage(roomId, memberId, "안녕하세요");

		// 저장 이후에만 브로드캐스트: publish된 메시지는 이미 DB id가 부여된 상태여야 한다.
		assertThat(publisher.lastRoomId).isEqualTo(roomId);
		assertThat(publisher.lastMessage).isNotNull();
		assertThat(publisher.lastMessage.id()).isNotNull().isEqualTo(sent.id());
		assertThat(publisher.lastMessage.content()).isEqualTo("안녕하세요");

		MockHttpSession ownerSession = new MockHttpSession();
		ownerSession.setAttribute(AuthController.SESSION_USER_ID, ownerId);
		mvc.perform(get("/api/chat/rooms").session(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].roomId").value((int) roomId))
				.andExpect(jsonPath("$[0].lastMessage").value("안녕하세요"))
				.andExpect(jsonPath("$[0].unreadCount").value(1));

		mvc.perform(post("/api/chat/rooms/" + roomId + "/read").session(ownerSession)
				.contentType(MediaType.APPLICATION_JSON).content("{\"lastReadMessageId\":" + sent.id() + "}"))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/chat/rooms").session(ownerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unreadCount").value(0));
	}

	private MockHttpSession sessionFor(String email) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user(email).getId());
		return session;
	}

	private User user(String email) {
		return users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
	}

	@TestConfiguration
	static class PublisherTestConfig {
		@Bean @Primary CapturingPublisher capturingPublisher() { return new CapturingPublisher(); }
	}

	static class CapturingPublisher implements ChatMessagePublisher {
		volatile Long lastRoomId;
		volatile ChatMessageResponse lastMessage;

		@Override public void publish(Long roomId, ChatMessageResponse message) {
			this.lastRoomId = roomId;
			this.lastMessage = message;
		}
	}
}
