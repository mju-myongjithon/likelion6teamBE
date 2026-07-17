package com.mju.mjuton.chat.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.chat.dto.ChatMessageResponse;
import com.mju.mjuton.chat.dto.ChatRoomSummaryResponse;
import com.mju.mjuton.chat.dto.MarkReadRequest;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "채팅", description = "모임 채팅방 목록·메시지 이력 조회와 읽음 처리를 제공합니다. 메시지 전송은 WebSocket(STOMP)으로 이뤄집니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class ChatRestController {
	private final ChatService chatService;

	public ChatRestController(ChatService chatService) {
		this.chatService = chatService;
	}

	@GetMapping("/rooms")
	@Operation(summary = "내 채팅방 목록 조회",
			description = "로그인 사용자가 리더 또는 멤버로 속한 모임들의 채팅방을 마지막 메시지·안 읽은 개수와 함께 조회합니다. "
					+ "프런트는 여기서 받은 roomId들을 STOMP로 구독합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "채팅방 목록 조회 성공"),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<ChatRoomSummaryResponse> myRooms(@Parameter(hidden = true) HttpServletRequest request) {
		return chatService.getMyRooms(currentUserId(request));
	}

	@GetMapping("/rooms/{roomId}/messages")
	@Operation(summary = "채팅 메시지 이력 조회",
			description = "채팅방 입장 시 최신 메시지부터 조회하고, 스크롤 시 before(해당 메시지 이전)와 size로 이전 페이지를 조회합니다. "
					+ "해당 방이 속한 모임의 멤버만 조회할 수 있습니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "메시지 이력 조회 성공"),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "채팅방 접근 권한 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<ChatMessageResponse> messages(
			@Parameter(description = "채팅방 id") @PathVariable Long roomId,
			@Parameter(description = "이 메시지 id 이전(더 과거) 메시지만 조회") @RequestParam(required = false) Long before,
			@Parameter(description = "한 번에 가져올 개수(기본 50)") @RequestParam(required = false) Integer size,
			@Parameter(hidden = true) HttpServletRequest request) {
		return chatService.getMessages(roomId, currentUserId(request), before, size);
	}

	@PostMapping("/rooms/{roomId}/read")
	@Operation(summary = "읽음 처리",
			description = "해당 채팅방에서 사용자가 마지막으로 읽은 메시지 id를 기록해 안 읽은 개수를 갱신합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "읽음 처리 완료"),
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "채팅방 접근 권한 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ResponseEntity<Void> markRead(
			@Parameter(description = "채팅방 id") @PathVariable Long roomId,
			@Valid @RequestBody MarkReadRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
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
