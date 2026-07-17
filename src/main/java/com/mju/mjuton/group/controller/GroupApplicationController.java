package com.mju.mjuton.group.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import com.mju.mjuton.group.service.GroupMembershipService;
import com.mju.mjuton.group.service.GroupMembershipService.MyApplicationPageResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group-applications")
@Tag(name = "내 모임 참가 신청", description = "로그인 사용자의 참가 신청 내역과 대기 신청을 관리합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class GroupApplicationController {
	private final GroupMembershipService memberships;

	public GroupApplicationController(GroupMembershipService memberships) {
		this.memberships = memberships;
	}

	@GetMapping("/me")
	@Operation(summary = "내 참가 신청 내역 조회",
			description = "상태 필터를 적용하고 최신 신청순으로 페이지 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "내 참가 신청 내역 조회 성공",
				content = @Content(schema = @Schema(implementation = MyApplicationPageResponse.class))),
		@ApiResponse(responseCode = "400", description = "필터 또는 페이지 규칙 위반",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "401", description = "로그인 필요",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	MyApplicationPageResponse myApplications(
			@Parameter(description = "신청 상태. 생략하면 모든 상태를 조회합니다.")
			@RequestParam(required = false) GroupJoinApplicationStatus status,
			@Parameter(description = "0부터 시작하는 페이지 번호",
					schema = @Schema(type = "integer", format = "int32", defaultValue = "0", minimum = "0"))
			@RequestParam(defaultValue = "0") int page,
			@Parameter(description = "페이지 크기",
					schema = @Schema(type = "integer", format = "int32",
							defaultValue = "20", minimum = "1", maximum = "100"))
			@RequestParam(defaultValue = "20") int size,
			@Parameter(hidden = true) HttpServletRequest request) {
		return memberships.myApplications(sessionUserId(request), status, page, size);
	}

	@PostMapping("/{applicationId}/cancel")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "대기 중인 참가 신청 취소")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "참가 신청 취소 성공"),
		@ApiResponse(responseCode = "401", description = "로그인 필요",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "404", description = "본인의 참가 신청 없음",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "409", description = "이미 처리된 참가 신청",
				content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	void cancel(@PathVariable long applicationId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.cancel(applicationId, sessionUserId(request));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
