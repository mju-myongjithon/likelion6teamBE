package com.mju.mjuton.chat.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.chat.dto.AddMemberRequest;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.dto.CreateRoomRequest;
import com.mju.mjuton.chat.dto.MarkReadRequest;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatRestController {
	private final ChatService chatService;

	public ChatRestController(ChatService chatService) {
		this.chatService = chatService;
	}

	/** 개발/테스트용 — 채팅방을 만들고 생성자를 곧바로 멤버로 등록한다. */
	@PostMapping("/rooms")
	ResponseEntity<ChatRoomSummaryResponse> createRoom(@Valid @RequestBody CreateRoomRequest body,
			HttpServletRequest request) {
		ChatRoomSummaryResponse room = chatService.createRoom(currentUserId(request), body.title());
		return ResponseEntity.status(HttpStatus.CREATED).body(room);
	}

	/** 개발/테스트용 — 요청자가 멤버인 방에 다른 사용자를 초대한다. */
	@PostMapping("/rooms/{roomId}/members")
	ResponseEntity<Void> addMember(@PathVariable Long roomId, @Valid @RequestBody AddMemberRequest body,
			HttpServletRequest request) {
		chatService.addMember(roomId, currentUserId(request), body.userId());
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/** 로그인 직후 프런트가 호출 — 여기서 받은 roomId들을 전부 STOMP subscribe한다. */
	@GetMapping("/rooms")
	List<ChatRoomSummaryResponse> myRooms(HttpServletRequest request) {
		return chatService.getMyRooms(currentUserId(request));
	}

	/** 채팅방 입장 시 최초 히스토리, 스크롤 시 before로 이전 페이지 조회. */
	@GetMapping("/rooms/{roomId}/messages")
	List<ChatMessageResponse> messages(@PathVariable Long roomId,
			@RequestParam(required = false) Long before,
			@RequestParam(required = false) Integer size,
			HttpServletRequest request) {
		return chatService.getMessages(roomId, currentUserId(request), before, size);
	}

	@PostMapping("/rooms/{roomId}/read")
	ResponseEntity<Void> markRead(@PathVariable Long roomId, @Valid @RequestBody MarkReadRequest body,
			HttpServletRequest request) {
		chatService.markAsRead(roomId, currentUserId(request), body.lastReadMessageId());
		return ResponseEntity.noContent().build();
	}

	private Long currentUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
