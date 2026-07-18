package com.mju.mjuton.cafe.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.cafe.service.CafeRecommendationService.CafeRecommendationResponse;
import com.mju.mjuton.cafe.service.GroupCafeRecommendationService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups/{groupId}/cafes")
@Tag(name = "카페 추천", description = "모임 멤버의 거주 지역 대표 좌표를 기반으로 가까운 카페를 최대 3개 추천합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class GroupCafeRecommendationController {
	private final GroupCafeRecommendationService groupCafeRecommendationService;

	public GroupCafeRecommendationController(GroupCafeRecommendationService groupCafeRecommendationService) {
		this.groupCafeRecommendationService = groupCafeRecommendationService;
	}

	@PostMapping("/recommendations")
	@Operation(summary = "모임 멤버 기반 공동 카페 추천",
			description = "프론트는 groupId만 전달합니다. 백엔드는 프로필 좌표를 사용하고, 좌표가 없으면 저장된 시·군·구의 시청·군청·구청을 대표 위치로 사용해 직선거리 기준 상위 3개 카페를 추천합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "카페 추천 성공",
					content = @Content(schema = @Schema(implementation = CafeRecommendationResponse.class))),
			@ApiResponse(responseCode = "400", description = "멤버 위치를 좌표나 거주 지역으로 확인할 수 없거나 외부 검색 결과가 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "모임 멤버가 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 없음 또는 추천 가능한 카페 후보 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "외부 카페 검색 API 사용 불가",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	CafeRecommendationResponse recommend(@PathVariable long groupId,
			@Parameter(hidden = true) HttpServletRequest request) {
		return groupCafeRecommendationService.recommend(groupId, sessionUserId(request));
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
