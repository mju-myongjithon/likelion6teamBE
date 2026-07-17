package com.mju.mjuton.cafe.service;

import com.mju.mjuton.cafe.service.CafeSearchClient.CafeCandidate;
import com.mju.mjuton.global.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CafeRecommendationService {
	private final CafeSearchClient cafeSearchClient;

	public CafeRecommendationService(CafeSearchClient cafeSearchClient) {
		this.cafeSearchClient = cafeSearchClient;
	}

	public CafeRecommendationResponse recommend(RecommendationValues values) {
		NormalizedValues normalized = normalize(values);
		Coordinate center = centerOf(normalized.users());
		List<CafeCandidate> candidates = deduplicateCafes(normalized.cafes() == null
				? cafeSearchClient.searchNearby(center.latitude(), center.longitude())
				: normalized.cafes());
		if (candidates.isEmpty()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CAFE_NOT_FOUND", "추천할 수 있는 카페 후보가 없습니다.");
		}
		List<ScoredCafe> selected = candidates.stream()
				.map(candidate -> score(candidate, normalized.users(), center))
				.sorted(Comparator.comparingDouble(ScoredCafe::totalDistanceMeters)
						.thenComparingDouble(ScoredCafe::maxDistanceMeters)
						.thenComparingDouble(ScoredCafe::centerDistanceMeters))
				.limit(3)
				.toList();
		return CafeRecommendationResponse.from(selected, normalized.users().size());
	}

	private NormalizedValues normalize(RecommendationValues values) {
		if (values == null) throw invalidRequest("요청 본문은 필수입니다.");
		if (values.users() == null || values.users().size() < 2) {
			throw invalidRequest("위치가 있는 사용자는 최소 2명 이상이어야 합니다.");
		}
		if (values.users().size() > 20) throw invalidRequest("사용자 위치는 최대 20개까지 입력할 수 있습니다.");
		List<UserLocation> users = values.users().stream().map(this::userLocation).toList();
		validateUniqueUserIds(users);
		List<CafeCandidate> cafes = values.cafes() == null ? null
				: values.cafes().stream().map(this::cafe).toList();
		return new NormalizedValues(users, cafes);
	}

	private UserLocation userLocation(UserLocation value) {
		if (value == null) throw invalidRequest("사용자 위치는 null일 수 없습니다.");
		validateLatitude(value.latitude(), "사용자 위도");
		validateLongitude(value.longitude(), "사용자 경도");
		return value;
	}

	private CafeCandidate cafe(CafeCandidate value) {
		if (value == null) throw invalidRequest("카페 후보는 null일 수 없습니다.");
		if (value.name() == null || value.name().trim().isEmpty() || value.name().length() > 100) {
			throw invalidRequest("카페 이름은 1~100자여야 합니다.");
		}
		validateLatitude(value.latitude(), "카페 위도");
		validateLongitude(value.longitude(), "카페 경도");
		return new CafeCandidate(value.name().trim(), value.latitude(), value.longitude(), value.address(),
				value.phone(), value.parkingAvailable(), value.parkingInfo());
	}

	private void validateUniqueUserIds(List<UserLocation> users) {
		Set<Long> uniqueIds = users.stream()
				.map(UserLocation::userId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		long idCount = users.stream().map(UserLocation::userId).filter(Objects::nonNull).count();
		if (uniqueIds.size() != idCount) throw invalidRequest("중복된 사용자 ID는 사용할 수 없습니다.");
	}

	private List<CafeCandidate> deduplicateCafes(List<CafeCandidate> cafes) {
		Map<String, CafeCandidate> unique = new LinkedHashMap<>();
		for (CafeCandidate cafe : cafes) unique.putIfAbsent(cafeKey(cafe), cafe);
		return List.copyOf(unique.values());
	}

	private String cafeKey(CafeCandidate cafe) {
		return "%s:%.6f:%.6f".formatted(
				cafe.name().trim().toLowerCase(Locale.ROOT), cafe.latitude(), cafe.longitude());
	}

	private void validateLatitude(double latitude, String field) {
		validateLatitude(Double.valueOf(latitude), field);
	}

	private void validateLatitude(Double latitude, String field) {
		if (latitude == null || Double.isNaN(latitude) || latitude < -90 || latitude > 90) {
			throw invalidRequest(field + "가 올바르지 않습니다.");
		}
	}

	private void validateLongitude(double longitude, String field) {
		validateLongitude(Double.valueOf(longitude), field);
	}

	private void validateLongitude(Double longitude, String field) {
		if (longitude == null || Double.isNaN(longitude) || longitude < -180 || longitude > 180) {
			throw invalidRequest(field + "가 올바르지 않습니다.");
		}
	}

	private Coordinate centerOf(List<UserLocation> users) {
		double latitude = users.stream().mapToDouble(UserLocation::latitude).average().orElseThrow();
		double longitude = users.stream().mapToDouble(UserLocation::longitude).average().orElseThrow();
		return new Coordinate(latitude, longitude);
	}

	private ScoredCafe score(CafeCandidate cafe, List<UserLocation> users, Coordinate center) {
		List<Double> distances = users.stream()
				.map(user -> haversineMeters(user.latitude(), user.longitude(), cafe.latitude(), cafe.longitude()))
				.toList();
		double total = distances.stream().mapToDouble(Double::doubleValue).sum();
		double max = distances.stream().mapToDouble(Double::doubleValue).max().orElse(0);
		double centerDistance = haversineMeters(center.latitude(), center.longitude(), cafe.latitude(), cafe.longitude());
		return new ScoredCafe(cafe, total, max, centerDistance);
	}

	private double haversineMeters(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
		double earthRadiusMeters = 6_371_000;
		double lat1 = Math.toRadians(fromLatitude);
		double lat2 = Math.toRadians(toLatitude);
		double deltaLat = Math.toRadians(toLatitude - fromLatitude);
		double deltaLng = Math.toRadians(toLongitude - fromLongitude);
		double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
				+ Math.cos(lat1) * Math.cos(lat2)
				* Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		return earthRadiusMeters * c;
	}

	private ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	public record RecommendationValues(List<UserLocation> users, List<CafeCandidate> cafes) {}
	public record UserLocation(Long userId, Double latitude, Double longitude) {}
	private record Coordinate(double latitude, double longitude) {}
	private record NormalizedValues(List<UserLocation> users, List<CafeCandidate> cafes) {}
	private record ScoredCafe(CafeCandidate cafe, double totalDistanceMeters, double maxDistanceMeters,
			double centerDistanceMeters) {}

	@Schema(name = "CafeRecommendationResponse", description = "좌표 기반 공동 카페 추천 결과")
	public record CafeRecommendationResponse(
			@Schema(description = "추천 카페 목록. 직선거리 기준 상위 3개까지 반환합니다.")
			List<CafeRecommendation> recommendations) {
		static CafeRecommendationResponse from(List<ScoredCafe> selected, int userCount) {
			List<CafeRecommendation> recommendations = java.util.stream.IntStream.range(0, selected.size())
					.mapToObj(index -> CafeRecommendation.from(index + 1, selected.get(index), userCount))
					.toList();
			return new CafeRecommendationResponse(recommendations);
		}
	}

	@Schema(name = "CafeRecommendation", description = "좌표 기반 공동 카페 추천 항목")
	public record CafeRecommendation(
			@Schema(description = "추천 순위", example = "1") int rank,
			@Schema(description = "추천 카페 위치 정보") CafeLocation location,
			@Schema(description = "추천 카페 상세 정보") CafeDetail detail,
			@Schema(description = "추천 이유. 직선거리 기준이며 도로 최단거리나 다익스트라 결과가 아닙니다.",
					example = "4명의 사용자 좌표와 카페 좌표 사이의 직선거리 기준으로 총 거리 약 1200m, 가장 먼 사용자 거리 약 400m로 후보 중 1순위입니다.")
			String reason) {
		static CafeRecommendation from(int rank, ScoredCafe selected, int userCount) {
			CafeCandidate cafe = selected.cafe();
			String reason = "%d명의 사용자 좌표와 카페 좌표 사이의 직선거리 기준으로 총 거리 약 %.0fm, 가장 먼 사용자 거리 약 %.0fm로 후보 중 %d순위입니다."
					.formatted(userCount, selected.totalDistanceMeters(), selected.maxDistanceMeters(), rank);
			return new CafeRecommendation(
					rank,
					new CafeLocation(cafe.name(), cafe.latitude(), cafe.longitude()),
					new CafeDetail(cafe.address(), cafe.phone(), cafe.parkingAvailable(), cafe.parkingInfo()),
					reason);
		}
	}

	@Schema(name = "CafeLocation", description = "추천 카페 위치 정보")
	public record CafeLocation(
			@Schema(description = "카페명", example = "캠퍼스 카페") String placeName,
			@Schema(description = "위도", example = "37.223") double latitude,
			@Schema(description = "경도", example = "127.1888") double longitude) {}
	@Schema(name = "CafeDetail", description = "추천 카페 상세 정보")
	public record CafeDetail(
			@Schema(description = "주소. 외부 검색 결과에 없으면 null일 수 있습니다.", nullable = true) String address,
			@Schema(description = "전화번호. 외부 검색 결과에 없으면 null일 수 있습니다.", nullable = true) String phone,
			@Schema(description = "주차 가능 여부. 카카오 Local 검색만 사용한 경우 확인되지 않아 null입니다.", nullable = true)
			Boolean parkingAvailable,
			@Schema(description = "주차 관련 설명. 외부 검색만 사용한 경우 확인 필요 문구가 반환됩니다.", nullable = true)
			String parkingInfo) {}
}
