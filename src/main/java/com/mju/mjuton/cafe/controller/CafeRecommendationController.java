package com.mju.mjuton.cafe.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.cafe.service.CafeRecommendationService;
import com.mju.mjuton.cafe.service.CafeRecommendationService.CafeRecommendationResponse;
import com.mju.mjuton.cafe.service.CafeRecommendationService.RecommendationValues;
import com.mju.mjuton.cafe.service.CafeRecommendationService.UserLocation;
import com.mju.mjuton.cafe.service.CafeSearchClient.CafeCandidate;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cafes")
@Tag(name = "카페 추천", description = "여러 사용자 좌표의 직선거리 기준으로 가까운 카페를 최대 3개 추천합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class CafeRecommendationController {
	private final CafeRecommendationService cafeRecommendationService;

	public CafeRecommendationController(CafeRecommendationService cafeRecommendationService) {
		this.cafeRecommendationService = cafeRecommendationService;
	}

	@PostMapping("/recommendations")
	@Operation(summary = "공동 카페 추천",
			description = "프론트가 전달한 사용자 현재 좌표를 기준으로 계산합니다. 도로 경로나 다익스트라가 아닌 사용자 좌표와 후보 카페 좌표 사이의 직선거리 합이 작은 순서로 최대 3개 카페를 추천합니다.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "카페 추천 성공",
					content = @Content(schema = @Schema(implementation = CafeRecommendationResponse.class))),
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "요청자 위치 누락",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "추천 가능한 카페 후보 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "503", description = "외부 카페 검색 API 사용 불가",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	CafeRecommendationResponse recommend(@Valid @RequestBody CafeRecommendationRequest body, HttpServletRequest request) {
		long userId = sessionUserId(request);
		if (body == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 본문은 필수입니다.");
		}
		RecommendationValues values = body.toValues();
		ensureRequesterIncluded(values, userId);
		return cafeRecommendationService.recommend(values);
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	private void ensureRequesterIncluded(RecommendationValues values, long requesterId) {
		if (values == null || values.users() == null) return;
		boolean hasUserIds = values.users().stream().anyMatch(user -> user != null && user.userId() != null);
		if (!hasUserIds) return;
		boolean containsRequester = values.users().stream()
				.anyMatch(user -> user != null && Long.valueOf(requesterId).equals(user.userId()));
		if (!containsRequester) {
			throw new ApiException(HttpStatus.FORBIDDEN, "CAFE_RECOMMENDATION_FORBIDDEN",
					"요청자 위치가 사용자 목록에 포함되어야 합니다.");
		}
	}

	@Schema(name = "CafeRecommendationRequest", description = "공동 카페 추천 요청")
	public record CafeRecommendationRequest(
			@ArraySchema(arraySchema = @Schema(description = "프론트에서 현재 위치 권한 등을 통해 확인한 사용자 좌표 목록",
					requiredMode = Schema.RequiredMode.REQUIRED),
					schema = @Schema(implementation = UserLocationRequest.class), minItems = 2)
			@NotNull(message = "사용자 위치 배열은 필수입니다.") List<UserLocationRequest> users,
			@ArraySchema(arraySchema = @Schema(description = "선택 카페 후보. 생략하면 외부 카페 검색 API를 사용하며, 이 경우 주차 정보는 확인되지 않을 수 있습니다."),
					schema = @Schema(implementation = CafeCandidateRequest.class))
			List<CafeCandidateRequest> cafes) {
		RecommendationValues toValues() {
			return new RecommendationValues(
					users == null ? null : users.stream()
							.map(user -> user == null ? null : user.toValues()).toList(),
					cafes == null ? null : cafes.stream()
							.map(cafe -> cafe == null ? null : cafe.toValues()).toList());
		}
	}

	public record UserLocationRequest(
			@Schema(description = "사용자 ID", example = "1", nullable = true) Long userId,
			@Schema(description = "사용자 위도", example = "37.2221", requiredMode = Schema.RequiredMode.REQUIRED)
			Double latitude,
			@Schema(description = "사용자 경도", example = "127.1876", requiredMode = Schema.RequiredMode.REQUIRED)
			Double longitude) {
		UserLocation toValues() { return new UserLocation(userId, latitude, longitude); }
	}

	public record CafeCandidateRequest(
			@Schema(description = "카페명", example = "캠퍼스 카페") String name,
			@Schema(description = "카페 위도", example = "37.2230", requiredMode = Schema.RequiredMode.REQUIRED)
			Double latitude,
			@Schema(description = "카페 경도", example = "127.1880", requiredMode = Schema.RequiredMode.REQUIRED)
			Double longitude,
			@Schema(description = "주소", example = "경기 용인시 처인구 명지로") String address,
			@Schema(description = "전화번호", example = "031-000-0000") String phone,
			@Schema(description = "주차 가능 여부", example = "true", nullable = true) Boolean parkingAvailable,
			@Schema(description = "주차 상세", example = "건물 뒤편 주차 가능") String parkingInfo) {
		CafeCandidate toValues() {
			return new CafeCandidate(name, latitude, longitude, address, phone, parkingAvailable, parkingInfo);
		}
	}
}
