package com.mju.mjuton.meetup.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.meetup.service.MeetupService;
import com.mju.mjuton.meetup.service.MeetupService.CreateValues;
import com.mju.mjuton.meetup.service.MeetupService.MeetupResponse;
import com.mju.mjuton.meetup.service.MeetupService.PlaceMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/meetups")
@Tag(name = "약속", description = "모임 채팅방의 약속 생성, 장소 투표, 확정을 관리합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class MeetupController {
	private final MeetupService meetupService;

	public MeetupController(MeetupService meetupService) {
		this.meetupService = meetupService;
	}

	@GetMapping
	@Operation(summary = "약속 목록 조회")
	List<MeetupResponse> list(@PathVariable long groupId, HttpServletRequest request) {
		return meetupService.list(groupId, sessionUserId(request));
	}

	@PostMapping
	@Operation(summary = "약속 및 장소 투표 생성")
	MeetupResponse create(@PathVariable long groupId, @Valid @RequestBody CreateRequest body,
			HttpServletRequest request) {
		return meetupService.create(groupId, sessionUserId(request), body.toValues());
	}

	@PutMapping("/{meetupId}/votes/{optionId}")
	@Operation(summary = "장소 후보 투표 또는 선택 변경")
	MeetupResponse vote(@PathVariable long groupId, @PathVariable long meetupId,
			@PathVariable long optionId, HttpServletRequest request) {
		return meetupService.vote(groupId, meetupId, optionId, sessionUserId(request));
	}

	@DeleteMapping("/{meetupId}/votes/me")
	@Operation(summary = "내 장소 투표 취소")
	MeetupResponse cancelVote(@PathVariable long groupId, @PathVariable long meetupId,
			HttpServletRequest request) {
		return meetupService.cancelVote(groupId, meetupId, sessionUserId(request));
	}

	@PostMapping("/{meetupId}/confirm")
	@Operation(summary = "최다 득표 장소로 약속 확정")
	MeetupResponse confirm(@PathVariable long groupId, @PathVariable long meetupId,
			HttpServletRequest request) {
		return meetupService.confirm(groupId, meetupId, sessionUserId(request));
	}

	@DeleteMapping("/{meetupId}")
	@Operation(summary = "약속 취소")
	void cancel(@PathVariable long groupId, @PathVariable long meetupId, HttpServletRequest request) {
		meetupService.cancel(groupId, meetupId, sessionUserId(request));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	public record CreateRequest(
			@NotBlank @Size(max = 100) String name,
			@NotNull LocalDate meetingDate,
			@NotNull LocalTime meetingTime,
			PlaceMode placeMode,
			@Size(max = 255) String customAddress) {
		CreateValues toValues() {
			return new CreateValues(name, meetingDate, meetingTime, placeMode, customAddress);
		}
	}
}
