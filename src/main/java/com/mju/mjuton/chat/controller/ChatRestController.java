package com.mju.mjuton.chat.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.chat.dto.ChatHistoryResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.dto.MarkReadRequest;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "모임 채팅", description = "참여 중인 모임의 채팅방, 메시지 이력과 읽음 상태를 관리합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class ChatRestController {
	private final ChatService chatService;

	public ChatRestController(ChatService chatService) {
		this.chatService = chatService;
	}

	@GetMapping("/rooms")
	@Operation(summary = "내 모임 채팅방 목록")
	List<ChatRoomSummaryResponse> rooms(@Parameter(hidden = true) HttpServletRequest request) {
		return chatService.rooms(currentUserId(request));
	}

	@GetMapping("/groups/{groupId}/messages")
	@Operation(summary = "모임 채팅 메시지 이력", description = "최신순 cursor 페이지를 조회합니다.")
	ChatHistoryResponse messages(@PathVariable long groupId,
			@RequestParam(required = false) Long before,
			@RequestParam(required = false) Integer size,
			@Parameter(hidden = true) HttpServletRequest request) {
		return chatService.messages(groupId, currentUserId(request), before, size);
	}

	@PostMapping("/groups/{groupId}/read")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 채팅 읽음 처리")
	void markRead(@PathVariable long groupId, @Valid @RequestBody MarkReadRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		chatService.markRead(groupId, currentUserId(request), body.messageId());
	}

	private long currentUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
