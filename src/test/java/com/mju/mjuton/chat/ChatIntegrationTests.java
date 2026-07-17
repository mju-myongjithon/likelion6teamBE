package com.mju.mjuton.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.repository.ChatMessageRepository;
import com.mju.mjuton.chat.repository.ChatReadStateRepository;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.group.service.GroupMembershipService;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class ChatIntegrationTests {
	@Autowired MockMvc mvc;
	@Autowired UserRepository users;
	@Autowired ProfileRepository profiles;
	@Autowired StudyGroupRepository groups;
	@Autowired GroupMemberRepository groupMembers;
	@Autowired ChatMessageRepository messages;
	@Autowired ChatReadStateRepository readStates;
	@Autowired GroupMembershipService memberships;
	@Autowired ChatService chatService;
	@Autowired PlatformTransactionManager transactionManager;
	@MockitoBean SimpMessagingTemplate messaging;

	@BeforeEach
	void clearData() {
		readStates.deleteAll();
		messages.deleteAll();
		groups.deleteAll();
	}

	@Test
	void roomHistoryReadAndAfterCommitFanoutFollowContract() throws Exception {
		MockHttpSession leader = sessionFor("chat-contract-leader@mju.ac.kr", "리더");
		MockHttpSession member = sessionFor("chat-contract-member@mju.ac.kr", "멤버");
		long groupId = createGroup(leader);
		approve(groupId, member, leader);

		ChatMessageResponse first = chatService.send(groupId, userId(member), "  첫 메시지  ");
		ChatMessageResponse second = chatService.send(groupId, userId(member), "둘째 메시지");
		verify(messaging).convertAndSendToUser(
				Long.toString(userId(leader)), "/queue/chat/groups/" + groupId, first);

		mvc.perform(get("/api/chat/rooms").session(member))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].groupId").value(groupId))
				.andExpect(jsonPath("$[0].title").value("채팅 스터디"))
				.andExpect(jsonPath("$[0].memberCount").value(2))
				.andExpect(jsonPath("$[0].lastMessageId").value(second.messageId()))
				.andExpect(jsonPath("$[0].lastMessage").value("둘째 메시지"))
				.andExpect(jsonPath("$[0].lastMessageAt").isNotEmpty())
				.andExpect(jsonPath("$[0].unreadCount").value(0));
		mvc.perform(get("/api/chat/rooms").session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unreadCount").value(2));

		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(leader)
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages.length()").value(1))
				.andExpect(jsonPath("$.messages[0].messageId").value(second.messageId()))
				.andExpect(jsonPath("$.messages[0].groupId").value(groupId))
				.andExpect(jsonPath("$.messages[0].senderId").value(userId(member)))
				.andExpect(jsonPath("$.messages[0].senderName").value("멤버"))
				.andExpect(jsonPath("$.messages[0].content").value("둘째 메시지"))
				.andExpect(jsonPath("$.messages[0].createdAt").isNotEmpty())
				.andExpect(jsonPath("$.nextCursor").value(second.messageId()))
				.andExpect(jsonPath("$.hasNext").value(true));

		mvc.perform(post("/api/chat/groups/{groupId}/read", groupId).session(leader)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messageId\":" + second.messageId() + "}"))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/chat/rooms").session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].unreadCount").value(0));
	}

	@Test
	void authenticationMembershipValidationAndCrossGroupReadAreEnforced() throws Exception {
		MockHttpSession leader = sessionFor("chat-auth-leader@mju.ac.kr", "리더");
		MockHttpSession outsider = sessionFor("chat-auth-outsider@mju.ac.kr", "외부인");
		long groupId = createGroup(leader);
		long otherGroupId = createGroup(outsider);
		ChatMessageResponse message = chatService.send(groupId, userId(leader), "메시지");

		mvc.perform(get("/api/chat/rooms"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(outsider))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("GROUP_MEMBER_REQUIRED"));
		mvc.perform(post("/api/chat/groups/{groupId}/read", otherGroupId).session(outsider)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messageId\":" + message.messageId() + "}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CHAT_MESSAGE_NOT_FOUND"));
		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(leader)
						.param("size", "101"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThatThrownBy(() -> chatService.send(groupId, userId(leader), "   "))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("INVALID_REQUEST"));
	}

	@Test
	void profilelessSenderUsesSafePlaceholderInsteadOfEmail() throws Exception {
		MockHttpSession leader = sessionForWithoutProfile("chat-profileless@mju.ac.kr");
		long groupId = createGroup(leader);

		ChatMessageResponse sent = chatService.send(groupId, userId(leader), "프로필 없는 메시지");

		assertThat(sent.senderName()).isEqualTo("알 수 없는 사용자");
		assertThat(sent.senderName()).doesNotContain("@");
		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(leader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages[0].senderName").value("알 수 없는 사용자"));
	}

	@Test
	void rolledBackSendDoesNotPublishAfterCommitEvent() throws Exception {
		MockHttpSession leader = sessionFor("chat-rollback-leader@mju.ac.kr", "리더");
		long groupId = createGroup(leader);

		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			chatService.send(groupId, userId(leader), "롤백 메시지");
			throw new IllegalStateException("rollback");
		})).isInstanceOf(IllegalStateException.class);

		verifyNoInteractions(messaging);
		assertThat(messages.count()).isZero();
	}

	@Test
	void concurrentReadUpdatesNeverMoveLastReadMessageBackward() throws Exception {
		MockHttpSession leader = sessionFor("chat-read-race-leader@mju.ac.kr", "리더");
		MockHttpSession member = sessionFor("chat-read-race-member@mju.ac.kr", "멤버");
		long groupId = createGroup(leader);
		approve(groupId, member, leader);
		ChatMessageResponse first = chatService.send(groupId, userId(member), "첫 메시지");
		ChatMessageResponse second = chatService.send(groupId, userId(member), "둘째 메시지");
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> newest = executor.submit(() -> {
				await(start);
				chatService.markRead(groupId, userId(leader), second.messageId());
			});
			Future<?> older = executor.submit(() -> {
				await(start);
				chatService.markRead(groupId, userId(leader), first.messageId());
			});
			start.countDown();
			newest.get();
			older.get();
		} finally {
			executor.shutdownNow();
		}

		assertThat(chatService.rooms(userId(leader)).get(0).unreadCount()).isZero();
	}

	@Test
	void leavingGroupImmediatelyRemovesRestAndSendAccess() throws Exception {
		MockHttpSession leader = sessionFor("chat-leave-leader@mju.ac.kr", "리더");
		MockHttpSession member = sessionFor("chat-leave-member@mju.ac.kr", "멤버");
		long groupId = createGroup(leader);
		approve(groupId, member, leader);
		assertThat(chatService.canAccess(groupId, userId(member))).isTrue();

		memberships.leave(groupId, userId(member));

		assertThat(chatService.canAccess(groupId, userId(member))).isFalse();
		assertThatThrownBy(() -> chatService.send(groupId, userId(member), "탈퇴 후 전송"))
				.isInstanceOfSatisfying(ApiException.class,
						exception -> assertThat(exception.getCode()).isEqualTo("GROUP_MEMBER_REQUIRED"));
		mvc.perform(get("/api/chat/groups/{groupId}/messages", groupId).session(member))
				.andExpect(status().isForbidden());
	}

	@Test
	void sendWaitsForConcurrentMembershipRemovalAndThenFailsClosed() throws Exception {
		MockHttpSession leader = sessionFor("chat-race-leader@mju.ac.kr", "리더");
		MockHttpSession member = sessionFor("chat-race-member@mju.ac.kr", "멤버");
		long groupId = createGroup(leader);
		approve(groupId, member, leader);
		CountDownLatch removalHasGroupLock = new CountDownLatch(1);
		CountDownLatch allowRemovalCommit = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Void> removal = executor.submit(() -> {
				new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
					groups.findByIdForUpdate(groupId).orElseThrow();
					groupMembers.delete(groupMembers.findByGroup_IdAndUser_Id(groupId, userId(member))
							.orElseThrow());
					groupMembers.flush();
					removalHasGroupLock.countDown();
					try {
						allowRemovalCommit.await();
					} catch (InterruptedException exception) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(exception);
					}
				});
				return null;
			});
			assertThat(removalHasGroupLock.await(2, TimeUnit.SECONDS)).isTrue();
			Future<String> send = executor.submit(() -> {
				try {
					chatService.send(groupId, userId(member), "경합 메시지");
					return "SENT";
				} catch (ApiException exception) {
					return exception.getCode();
				}
			});
			assertThatThrownBy(() -> send.get(200, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);
			allowRemovalCommit.countDown();
			removal.get();
			assertThat(send.get()).isEqualTo("GROUP_MEMBER_REQUIRED");
			assertThat(messages.count()).isZero();
		} finally {
			allowRemovalCommit.countDown();
			executor.shutdownNow();
		}
	}

	private long createGroup(MockHttpSession leader) throws Exception {
		MvcResult result = mvc.perform(post("/api/groups").session(leader)
						.contentType(MediaType.APPLICATION_JSON).content(
								"{\"title\":\"채팅 스터디\",\"description\":\"설명\","
								+ "\"maxMemberCount\":5,\"meetingRule\":\"매주\","
								+ "\"location\":\"서울\",\"recruitingRoles\":[]}"))
				.andExpect(status().isCreated()).andReturn();
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.groupId")).longValue();
	}

	private void approve(long groupId, MockHttpSession member, MockHttpSession leader) {
		long applicationId = memberships.apply(groupId, userId(member)).applicationId();
		memberships.approve(groupId, applicationId, userId(leader));
	}

	private MockHttpSession sessionFor(String email, String name) {
		User user = users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
		profiles.saveAndFlush(new Profile(user, name, "명지대학교", "컴퓨터공학과", "서울", null, null));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private MockHttpSession sessionForWithoutProfile(String email) {
		User user = users.saveAndFlush(new User(email, new BCryptPasswordEncoder().encode("password123")));
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, user.getId());
		return session;
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private long userId(MockHttpSession session) {
		return (Long) session.getAttribute(AuthController.SESSION_USER_ID);
	}
}
