package com.mju.mjuton.scrap.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.scrap.service.ScrapItem;
import com.mju.mjuton.scrap.service.ScrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scraps")
@Tag(name = "스크랩", description = "스터디 모임과 해커톤·행사를 개인 목록에 저장합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class ScrapController {
	private final ScrapService scraps;

	public ScrapController(ScrapService scraps) {
		this.scraps = scraps;
	}

	@PutMapping("/groups/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "스터디 모임 저장")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "저장됨"),
		@ApiResponse(responseCode = "401", description = "로그인 필요",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "모임 없음",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	void saveGroup(@PathVariable long groupId, @Parameter(hidden = true) HttpServletRequest request) {
		scraps.saveGroup(sessionUserId(request), groupId);
	}

	@DeleteMapping("/groups/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "스터디 모임 저장 해제")
	void removeGroup(@PathVariable long groupId, @Parameter(hidden = true) HttpServletRequest request) {
		scraps.removeGroup(sessionUserId(request), groupId);
	}

	@PutMapping("/events/{eventId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "해커톤·행사 저장")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "저장됨"),
		@ApiResponse(responseCode = "401", description = "로그인 필요",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "행사 없음",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	void saveEvent(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		scraps.saveEvent(sessionUserId(request), eventId);
	}

	@DeleteMapping("/events/{eventId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "해커톤·행사 저장 해제")
	void removeEvent(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		scraps.removeEvent(sessionUserId(request), eventId);
	}

	@GetMapping("/me")
	@Operation(summary = "내 통합 저장 목록 조회", description = "최근 저장한 항목부터 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "저장 목록 조회 성공",
				content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScrapItem.class)))),
		@ApiResponse(responseCode = "401", description = "로그인 필요",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<ScrapItem> findMine(@Parameter(hidden = true) HttpServletRequest request) {
		return scraps.findMine(sessionUserId(request));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
