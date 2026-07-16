package com.mju.mjuton.event.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.event.service.EventService;
import com.mju.mjuton.event.service.EventService.EventDetail;
import com.mju.mjuton.event.service.EventService.EventSummary;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
@Tag(name = "해커톤·행사", description = "내부 신청 기능 없이 행사 정보를 등록·조회·수정·삭제합니다.")
public class EventController {
	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "행사 정보 등록", description = "세션 사용자를 등록자로 하는 HACKATHON 행사 정보를 등록합니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "행사 등록 성공"),
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	EventDetail create(@Valid @RequestBody EventRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return eventService.create(sessionUserId(request), body.toValues());
	}

	@GetMapping
	@Operation(summary = "행사 목록 조회", description = "최신 등록순으로 공개 목록을 조회합니다.")
	List<EventSummary> findAll() {
		return eventService.findAll();
	}

	@GetMapping("/{eventId}")
	@Operation(summary = "행사 상세 조회", description = "행사 정보만 제공하며 내부 신청 기능은 제공하지 않습니다.")
	@ApiResponse(responseCode = "404", description = "행사 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	EventDetail find(@PathVariable long eventId) {
		return eventService.find(eventId);
	}

	@PutMapping("/{eventId}")
	@Operation(summary = "행사 정보 전체 수정", description = "행사 등록자만 태그를 포함한 모든 필드를 교체할 수 있습니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "등록자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "행사 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	EventDetail update(@PathVariable long eventId, @Valid @RequestBody EventRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return eventService.update(sessionUserId(request), eventId, body.toValues());
	}

	@DeleteMapping("/{eventId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "행사 정보 삭제", description = "행사 등록자만 삭제할 수 있습니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "행사 삭제 성공"),
			@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "등록자가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "행사 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	void delete(@PathVariable long eventId, @Parameter(hidden = true) HttpServletRequest request) {
		eventService.delete(sessionUserId(request), eventId);
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	@Schema(name = "EventRequest", description = "해커톤·행사 정보 등록·수정 요청")
	public record EventRequest(
			@Schema(example = "2026 CampusLink 해커톤", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(example = "대학생이 팀을 이루어 서비스를 개발하는 행사입니다.", minLength = 1, maxLength = 2000,
					requiredMode = Schema.RequiredMode.REQUIRED) String description,
			@Schema(example = "CampusLink", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String organizer,
			@Schema(example = "2026-08-01T14:59:59Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant applicationDeadlineAt,
			@Schema(example = "2026-08-08T00:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant startsAt,
			@Schema(example = "2026-08-09T09:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant endsAt,
			@Schema(example = "명지대학교 인문캠퍼스", minLength = 1, maxLength = 200,
					requiredMode = Schema.RequiredMode.REQUIRED) String location,
			@Schema(example = "https://example.com/events/campuslink-2026", minLength = 1, maxLength = 2048,
					requiredMode = Schema.RequiredMode.REQUIRED) String relatedUrl,
			@ArraySchema(arraySchema = @Schema(description = "행사 분류 태그", requiredMode = Schema.RequiredMode.REQUIRED),
					schema = @Schema(type = "string", minLength = 1, maxLength = 50), maxItems = 20, uniqueItems = true)
			@NotNull(message = "행사 태그 배열은 필수입니다.") List<String> tags) {
		EventService.EventValues toValues() {
			return new EventService.EventValues(title, description, organizer, applicationDeadlineAt, startsAt,
					endsAt, location, relatedUrl, tags);
		}
	}
}
