package com.mju.mjuton.eventapplication.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.eventapplication.service.EventApplicationService;
import com.mju.mjuton.eventapplication.service.EventApplicationService.ApplicationStatus;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/application")
@Tag(name = "행사 신청 표시", description = "외부 주최 측 접수와 별개로 CampusLink 내 신청 일정을 기록합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class EventApplicationController {
	private final EventApplicationService applications;

	public EventApplicationController(EventApplicationService applications) {
		this.applications = applications;
	}

	@PutMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "행사 신청 일정 기록")
	void apply(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		applications.apply(sessionUserId(request), eventId);
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "행사 신청 일정 취소")
	void cancel(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		applications.cancel(sessionUserId(request), eventId);
	}

	@GetMapping
	@Operation(summary = "행사 신청 일정 기록 여부 조회")
	ApplicationStatus status(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		return applications.status(sessionUserId(request), eventId);
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
