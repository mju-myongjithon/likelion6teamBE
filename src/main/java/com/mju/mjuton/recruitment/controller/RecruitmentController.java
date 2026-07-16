package com.mju.mjuton.recruitment.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.recruitment.dto.CreateRecruitmentRequest;
import com.mju.mjuton.recruitment.dto.JoinRequestResponse;
import com.mju.mjuton.recruitment.dto.RecruitmentResponse;
import com.mju.mjuton.recruitment.dto.TransferOwnershipRequest;
import com.mju.mjuton.recruitment.service.RecruitmentService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruitments")
public class RecruitmentController {
	private final RecruitmentService recruitmentService;

	public RecruitmentController(RecruitmentService recruitmentService) {
		this.recruitmentService = recruitmentService;
	}

	@PostMapping
	ResponseEntity<RecruitmentResponse> create(@Valid @RequestBody CreateRecruitmentRequest body,
			HttpServletRequest request) {
		RecruitmentResponse response = RecruitmentResponse.from(
				recruitmentService.create(currentUserId(request), body.title(), body.description(), body.capacity()));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/** 모집글 목록 — 설명을 보고 신청 여부를 정하도록 공개 조회한다. */
	@GetMapping
	List<RecruitmentResponse> list() {
		return recruitmentService.findAll().stream().map(RecruitmentResponse::from).toList();
	}

	@GetMapping("/{recruitmentId}")
	RecruitmentResponse detail(@PathVariable Long recruitmentId) {
		return RecruitmentResponse.from(recruitmentService.find(recruitmentId));
	}

	/** 방장이 직접 마감. */
	@PostMapping("/{recruitmentId}/close")
	ResponseEntity<Void> close(@PathVariable Long recruitmentId, HttpServletRequest request) {
		recruitmentService.close(recruitmentId, currentUserId(request));
		return ResponseEntity.noContent().build();
	}

	/** 방장 양도 — 채팅방 멤버인 다른 사용자에게 방장을 넘긴다. */
	@PostMapping("/{recruitmentId}/transfer")
	ResponseEntity<Void> transfer(@PathVariable Long recruitmentId, @Valid @RequestBody TransferOwnershipRequest body,
			HttpServletRequest request) {
		recruitmentService.transferOwnership(recruitmentId, currentUserId(request), body.newOwnerId());
		return ResponseEntity.noContent().build();
	}

	/** 방 나가기 — 방장은 먼저 양도해야 하며, 일반 멤버는 명단에서 제거된다. */
	@PostMapping("/{recruitmentId}/leave")
	ResponseEntity<Void> leave(@PathVariable Long recruitmentId, HttpServletRequest request) {
		recruitmentService.leave(recruitmentId, currentUserId(request));
		return ResponseEntity.noContent().build();
	}

	/** 참가신청. */
	@PostMapping("/{recruitmentId}/applications")
	ResponseEntity<JoinRequestResponse> apply(@PathVariable Long recruitmentId, HttpServletRequest request) {
		JoinRequestResponse response = JoinRequestResponse.from(
				recruitmentService.apply(recruitmentId, currentUserId(request)));
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	/** 방장이 보는 대기 중인 신청 목록. */
	@GetMapping("/{recruitmentId}/applications")
	List<JoinRequestResponse> applications(@PathVariable Long recruitmentId, HttpServletRequest request) {
		return recruitmentService.pendingRequests(recruitmentId, currentUserId(request)).stream()
				.map(JoinRequestResponse::from).toList();
	}

	@PostMapping("/{recruitmentId}/applications/{requestId}/approve")
	ResponseEntity<Void> approve(@PathVariable Long recruitmentId, @PathVariable Long requestId,
			HttpServletRequest request) {
		recruitmentService.approve(recruitmentId, requestId, currentUserId(request));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{recruitmentId}/applications/{requestId}/reject")
	ResponseEntity<Void> reject(@PathVariable Long recruitmentId, @PathVariable Long requestId,
			HttpServletRequest request) {
		recruitmentService.reject(recruitmentId, requestId, currentUserId(request));
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
