package com.mju.mjuton.recommendation.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.listing.service.ListingFilter;
import com.mju.mjuton.recommendation.service.RecommendationService;
import com.mju.mjuton.recommendation.service.RecommendationService.RecommendationItem;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "AI 추천", description = "현재 사용자 프로필을 바탕으로 신청 가능한 모임과 행사를 추천합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class RecommendationController {
	private final RecommendationService recommendationService;

	public RecommendationController(RecommendationService recommendationService) {
		this.recommendationService = recommendationService;
	}

	@GetMapping
	@Operation(summary = "개인 맞춤 모임·행사 추천",
			description = "규칙 기반 적합도와 OpenAI 의미 평가를 결합하며, AI 장애 시 규칙 기반 결과를 반환합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "추천 조회 성공",
					content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
							array = @ArraySchema(schema = @Schema(implementation = RecommendationItem.class)))),
			@ApiResponse(responseCode = "400", description = "잘못된 필터 또는 개수",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "세션이 없거나 유효하지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<RecommendationItem> recommend(
			@Parameter(description = "추천 유형", schema = @Schema(
					allowableValues = {"ALL", "STUDY", "HACKATHON"}, defaultValue = "ALL"))
			@RequestParam(required = false) String filter,
			@Parameter(description = "추천 개수", schema = @Schema(defaultValue = "10", minimum = "1", maximum = "20"))
			@RequestParam(defaultValue = "10") int limit,
			@Parameter(hidden = true) HttpServletRequest request) {
		if (limit < 1 || limit > 20) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "limit은 1~20이어야 합니다.");
		}
		return recommendationService.recommend(sessionUserId(request), ListingFilter.from(filter), limit);
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
