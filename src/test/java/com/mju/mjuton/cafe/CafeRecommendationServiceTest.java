package com.mju.mjuton.cafe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mju.mjuton.cafe.service.CafeRecommendationService;
import com.mju.mjuton.cafe.service.CafeRecommendationService.RecommendationValues;
import com.mju.mjuton.cafe.service.CafeRecommendationService.UserLocation;
import com.mju.mjuton.cafe.service.CafeSearchClient;
import com.mju.mjuton.cafe.service.CafeSearchClient.CafeCandidate;
import com.mju.mjuton.global.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class CafeRecommendationServiceTest {
	@Test
	void recommendationReasonExplicitlyUsesStraightLineDistance() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		var response = service.recommend(new RecommendationValues(users(), List.of(
				new CafeCandidate("중앙 카페", 37.2230, 127.1888, "명지대 앞", null, null, null),
				new CafeCandidate("먼 카페", 37.2500, 127.2200, "먼 주소", null, null, null))));

		var first = response.recommendations().get(0);
		assertThat(first.location().placeName()).isEqualTo("중앙 카페");
		assertThat(first.reason()).contains("직선거리 기준");
		assertThat(first.reason()).doesNotContain("최단").doesNotContain("다익스트라");
	}

	@Test
	void missingParkingInformationRemainsUnknown() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		var response = service.recommend(new RecommendationValues(users(), List.of(
				new CafeCandidate("주차 미확인 카페", 37.2230, 127.1888, "명지대 앞", null, null,
						"외부 검색 결과에 주차 정보가 없어 확인이 필요합니다."))));

		assertThat(response.recommendations().get(0).detail().parkingAvailable()).isNull();
		assertThat(response.recommendations().get(0).detail().parkingInfo()).contains("확인이 필요");
	}

	@Test
	void emptyExternalSearchResultReturnsNotFound() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		assertThatThrownBy(() -> service.recommend(new RecommendationValues(users(), null)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
					assertThat(exception.getCode()).isEqualTo("CAFE_NOT_FOUND");
				});
	}

	@Test
	void externalSearchFailureIsPropagatedAsServiceUnavailable() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAFE_SEARCH_FAILED",
					"외부 카페 검색 API 호출에 실패했습니다.");
		});

		assertThatThrownBy(() -> service.recommend(new RecommendationValues(users(), null)))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(exception.getCode()).isEqualTo("CAFE_SEARCH_FAILED");
				});
	}

	@Test
	void duplicateUserIdsAreRejected() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		assertThatThrownBy(() -> service.recommend(new RecommendationValues(List.of(
				new UserLocation(1L, 37.2210, 127.1860),
				new UserLocation(1L, 37.2220, 127.1880)), List.of(
				new CafeCandidate("중앙 카페", 37.2230, 127.1888, "명지대 앞", null, null, null)))))
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(exception.getCode()).isEqualTo("INVALID_REQUEST");
				});
	}

	@Test
	void duplicateCafeCandidatesAreIgnored() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		var response = service.recommend(new RecommendationValues(users(), List.of(
				new CafeCandidate("중앙 카페", 37.2230, 127.1888, "명지대 앞", null, true, "주차 가능"),
				new CafeCandidate("중앙 카페", 37.2230, 127.1888, "중복 주소", null, false, "중복"),
				new CafeCandidate("먼 카페", 37.2500, 127.2200, "먼 주소", null, null, null))));

		assertThat(response.recommendations().get(0).location().placeName()).isEqualTo("중앙 카페");
		assertThat(response.recommendations().get(0).detail().parkingAvailable()).isTrue();
		assertThat(response.recommendations().get(0).detail().parkingInfo()).isEqualTo("주차 가능");
	}

	@Test
	void returnsTopThreeRecommendationsOnly() {
		CafeRecommendationService service = new CafeRecommendationService((latitude, longitude) -> List.of());

		var response = service.recommend(new RecommendationValues(users(), List.of(
				new CafeCandidate("1순위 카페", 37.2230, 127.1888, "주소1", null, null, null),
				new CafeCandidate("2순위 카페", 37.2240, 127.1895, "주소2", null, null, null),
				new CafeCandidate("3순위 카페", 37.2250, 127.1910, "주소3", null, null, null),
				new CafeCandidate("4순위 카페", 37.2500, 127.2200, "주소4", null, null, null))));

		assertThat(response.recommendations()).hasSize(3);
		assertThat(response.recommendations()).extracting("rank").containsExactly(1, 2, 3);
	}

	private List<UserLocation> users() {
		return List.of(
				new UserLocation(1L, 37.2210, 127.1860),
				new UserLocation(2L, 37.2220, 127.1880),
				new UserLocation(3L, 37.2240, 127.1900),
				new UserLocation(4L, 37.2250, 127.1910));
	}
}
