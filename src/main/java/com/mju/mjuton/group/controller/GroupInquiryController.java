package com.mju.mjuton.group.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.group.service.GroupInquiryService;
import com.mju.mjuton.group.service.GroupInquiryService.InquiryPageResponse;
import com.mju.mjuton.group.service.GroupInquiryService.InquiryResponse;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/inquiries")
@Tag(name = "모임 문의", description = "모임 문의를 공개 조회하고 작성자 삭제와 리더 답변을 관리합니다.")
public class GroupInquiryController {
	private final GroupInquiryService inquiryService;

	public GroupInquiryController(GroupInquiryService inquiryService) {
		this.inquiryService = inquiryService;
	}

	@GetMapping
	@Operation(summary = "모임 문의 목록 조회", description = "최신 문의부터 공개 조회합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "모임 문의 목록 조회 성공",
					content = @Content(schema = @Schema(implementation = InquiryPageResponse.class))),
			@ApiResponse(responseCode = "400", description = "페이지 요청값 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	InquiryPageResponse findAll(@PathVariable long groupId,
			@Parameter(schema = @Schema(minimum = "0", defaultValue = "0"))
			@RequestParam(defaultValue = "0") int page,
			@Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "5"))
			@RequestParam(defaultValue = "5") int size,
			@Parameter(hidden = true) HttpServletRequest request) {
		return inquiryService.findAll(groupId, optionalSessionUserId(request), page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "모임 문의 등록")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "모임 문의 등록 성공"),
			@ApiResponse(responseCode = "400", description = "문의 내용 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	InquiryResponse create(@PathVariable long groupId, @Valid @RequestBody InquiryContentRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return inquiryService.create(groupId, sessionUserId(request), body.content());
	}

	@DeleteMapping("/{inquiryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "모임 문의 삭제", description = "문의 작성자만 삭제할 수 있습니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "모임 문의 삭제 성공"),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "문의 작성자가 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 또는 문의 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	void delete(@PathVariable long groupId, @PathVariable long inquiryId,
			@Parameter(hidden = true) HttpServletRequest request) {
		inquiryService.delete(groupId, inquiryId, sessionUserId(request));
	}

	@PostMapping("/{inquiryId}/answer")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "모임 문의 답변", description = "모임 리더만 답변할 수 있으며 문의당 답변은 하나입니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "모임 문의 답변 성공"),
			@ApiResponse(responseCode = "400", description = "답변 내용 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "모임 리더가 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 또는 문의 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 답변된 문의",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	InquiryResponse answer(@PathVariable long groupId, @PathVariable long inquiryId,
			@Valid @RequestBody InquiryContentRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return inquiryService.answer(groupId, inquiryId, sessionUserId(request), body.content());
	}

	private Long optionalSessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		return session != null && session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId
				? userId : null;
	}

	private long sessionUserId(HttpServletRequest request) {
		Long userId = optionalSessionUserId(request);
		if (userId == null) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	@Schema(name = "GroupInquiryContentRequest", description = "모임 문의 또는 답변 내용")
	public record InquiryContentRequest(
			@NotBlank(message = "내용은 필수입니다.")
			@Size(min = 1, max = 1000, message = "내용은 1~1000자여야 합니다.")
			@Schema(minLength = 1, maxLength = 1000, requiredMode = Schema.RequiredMode.REQUIRED)
			String content) {}
}
