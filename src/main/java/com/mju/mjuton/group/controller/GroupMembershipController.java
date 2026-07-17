package com.mju.mjuton.group.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.group.service.GroupMembershipService;
import com.mju.mjuton.group.service.GroupMembershipService.ApplicationResponse;
import com.mju.mjuton.group.service.GroupMembershipService.MemberResponse;
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
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}")
@Tag(name = "모임 참여 관리", description = "모임 참가 신청, 승인, 멤버와 리더 권한을 관리합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class GroupMembershipController {
	private final GroupMembershipService memberships;

	public GroupMembershipController(GroupMembershipService memberships) {
		this.memberships = memberships;
	}

	@PostMapping("/applications")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "모임 참가 신청")
	ApplicationResponse apply(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		return memberships.apply(groupId, sessionUserId(request));
	}

	@GetMapping("/applications")
	@Operation(summary = "대기 중인 참가 신청 조회", description = "모임 리더만 조회할 수 있습니다.")
	List<ApplicationResponse> applications(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		return memberships.pendingApplications(groupId, sessionUserId(request));
	}

	@GetMapping("/applications/me")
	@Operation(summary = "내 참가 신청 조회")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 참가 신청 조회 성공",
					content = @Content(schema = @Schema(implementation = ApplicationResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 또는 참가 신청 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	ApplicationResponse myApplication(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		return memberships.myApplication(groupId, sessionUserId(request));
	}

	@PostMapping("/applications/{applicationId}/approve")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "참가 신청 승인", description = "모임 리더만 승인할 수 있습니다.")
	void approve(@PathVariable long groupId, @PathVariable long applicationId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.approve(groupId, applicationId, sessionUserId(request));
	}

	@PostMapping("/applications/{applicationId}/reject")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "참가 신청 거절", description = "모임 리더만 거절할 수 있습니다.")
	void reject(@PathVariable long groupId, @PathVariable long applicationId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.reject(groupId, applicationId, sessionUserId(request));
	}

	@GetMapping("/members")
	@Operation(summary = "모임 참여자 조회", description = "모임 참여자만 조회할 수 있습니다.")
	List<MemberResponse> members(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		return memberships.members(groupId, sessionUserId(request));
	}

	@DeleteMapping("/members/{memberUserId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 참여자 강퇴", description = "모임 리더만 할 수 있습니다.")
	void removeMember(@PathVariable long groupId, @PathVariable long memberUserId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.removeMember(groupId, memberUserId, sessionUserId(request));
	}

	@PostMapping("/leave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 탈퇴")
	void leave(@PathVariable long groupId, @Parameter(hidden = true) HttpServletRequest request) {
		memberships.leave(groupId, sessionUserId(request));
	}

	@PostMapping("/transfer-leader")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "리더 권한 양도", description = "현재 리더가 기존 참여자에게 권한을 양도합니다.")
	void transferLeadership(@PathVariable long groupId, @Valid @RequestBody TransferLeaderRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.transferLeadership(groupId, body.newLeaderUserId(), sessionUserId(request));
	}

	@PostMapping("/close")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 모집 마감", description = "모임 리더만 할 수 있습니다.")
	void closeRecruitment(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.closeRecruitment(groupId, sessionUserId(request));
	}

	@PostMapping("/reopen")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 모집 재개", description = "모임 리더만 할 수 있습니다.")
	void reopenRecruitment(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		memberships.reopenRecruitment(groupId, sessionUserId(request));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	public record TransferLeaderRequest(
			@NotNull(message = "새 리더 사용자 id는 필수입니다.") Long newLeaderUserId) {}
}
