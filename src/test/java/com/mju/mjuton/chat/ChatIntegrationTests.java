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
import com.mju.mjuton.chat.repository.ChatMessageRepository;
import com.mju.mjuton.chat.repository.ChatRoomRepository;
import com.mju.mjuton.chat.service.ChatMessagePublisher;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.group.service.GroupMembershipService;
import com.mju.mjuton.group.service.GroupMembershipService.ApplicationResponse;
import com.mju.mjuton.group.service.GroupService;
import com.mju.mjuton.group.service.GroupService.GroupDetail;
import com.mju.mjuton.group.service.GroupService.GroupValues;
import java.util.List;
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
	@Autowired GroupService groupService;
	@Autowired GroupMembershipService memberships;
	@Autowired StudyGroupRepository groups;
	@Autowired ChatRoomRepository rooms;
	@Autowired ChatMessageRepository chatMessages;
	@Autowired CapturingPublisher publisher;

	@BeforeEach
	void resetPublisher() {
		publisher.lastRoomId = null;
		publisher.lastMessage = null;
	}

	@Test
	void myRoomsListsRoomsOfGroupsIBelongTo() throws Exception {
		long leaderId = user("rooms-owner@mju.ac.kr").getId();
		createGroup(leaderId, "백엔드 스터디");
		createGroup(leaderId, "프론트 스터디");

		mvc.perform(get("/api/chat/rooms").session(sessionFor(leaderId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].unreadCount").value(0));
	}

	@Test
	void nonGroupMemberCannotAccessRoom() throws Exception {
		long leaderId = user("send-owner@mju.ac.kr").getId();
		long outsiderId = user("send-outsider@mju.ac.kr").getId();
		long roomId = createGroup(leaderId, "비공개 방");

		// 서비스 계층: 그룹 멤버가 아닌 사용자의 전송은 거부된다.
		assertThatThrownBy(() -> chatService.sendMessage(roomId, outsiderId, "몰래 보내기"))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
					assertThat(exception.getCode()).isEqualTo("NOT_ROOM_MEMBER");
				});
		assertThat(publisher.lastMessage).isNull(); // 거부된 전송은 브로드캐스트되지 않는다.

		// REST 히스토리 조회도 그룹 멤버가 아니면 거부된다.
		mvc.perform(get("/api/chat/rooms/" + roomId + "/messages").session(sessionFor(outsiderId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("NOT_ROOM_MEMBER"));
	}

	@Test
	void approvedApplicantGainsChatAccessAutomatically() throws Exception {
		long leaderId = user("flow-owner@mju.ac.kr").getId();
		long applicantId = user("flow-member@mju.ac.kr").getId();
		long groupId = createGroupId(leaderId, "협업 방");
		long roomId = roomIdOf(groupId);

		// 승인 전엔 접근 불가.
		assertThat(chatService.canAccess(roomId, applicantId)).isFalse();

		ApplicationResponse application = memberships.apply(groupId, applicantId);
		memberships.approve(groupId, application.applicationId(), leaderId);

		// 그룹 멤버가 되는 순간 채팅 접근이 자동으로 열린다(별도 동기화 없음).
		assertThat(chatService.canAccess(roomId, applicantId)).isTrue();

		ChatMessageResponse sent = chatService.sendMessage(roomId, applicantId, "안녕하세요");

		// 저장 이후에만 브로드캐스트: publish된 메시지는 이미 DB id가 부여된 상태여야 한다.
		assertThat(publisher.lastRoomId).isEqualTo(roomId);
		assertThat(publisher.lastMessage).isNotNull();
		assertThat(publisher.lastMessage.id()).isNotNull().isEqualTo(sent.id());
		assertThat(publisher.lastMessage.content()).isEqualTo("안녕하세요");

		MockHttpSession leaderSession = sessionFor(leaderId);
		mvc.perform(get("/api/chat/rooms").session(leaderSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].roomId").value((int) roomId))
				.andExpect(jsonPath("$[0].lastMessage").value("안녕하세요"))
				.andExpect(jsonPath("$[0].unreadCount").value(1));

		mvc.perform(post("/api/chat/rooms/" + roomId + "/read").session(leaderSession)
				.contentType(MediaType.APPLICATION_JSON).content("{\"lastReadMessageId\":" + sent.id() + "}"))
				.andExpect(status().isNoContent());

		mvc.perform(get("/api/chat/rooms").session(leaderSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unreadCount").value(0));
	}

	@Test
	void deletingGroupRemovesItsChatRoomAndMessages() {
		long leaderId = user("delete-owner@mju.ac.kr").getId();
		long groupId = createGroupId(leaderId, "삭제될 모임");
		long roomId = roomIdOf(groupId);
		ChatMessageResponse sent = chatService.sendMessage(roomId, leaderId, "지워질 메시지");
		chatService.markAsRead(roomId, leaderId, sent.id()); // 읽음 상태 행도 하나 생성해 둔다.

		groupService.delete(leaderId, groupId);

		// 방·메시지·접근권이 모두 함께 사라지고 고아 데이터가 남지 않는다.
		assertThat(rooms.findById(roomId)).isEmpty();
		assertThat(chatMessages.countByRoomIdAndIdGreaterThan(roomId, 0L)).isZero();
		assertThat(chatService.canAccess(roomId, leaderId)).isFalse();
	}

	/** 모임을 만들고 그 모임에 링크된 채팅방 id를 돌려준다. */
	private long createGroup(long leaderId, String title) {
		return roomIdOf(createGroupId(leaderId, title));
	}

	private long createGroupId(long leaderId, String title) {
		GroupDetail detail = groupService.create(leaderId,
				new GroupValues(title, title + " 설명", 10, "매주 월요일 저녁", "공학관 401호", List.of()));
		return detail.groupId();
	}

	private long roomIdOf(long groupId) {
		StudyGroup group = groups.findById(groupId).orElseThrow();
		return group.getChatRoomId();
	}

	private MockHttpSession sessionFor(long userId) {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, userId);
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
