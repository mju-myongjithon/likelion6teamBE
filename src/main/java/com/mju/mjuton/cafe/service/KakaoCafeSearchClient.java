package com.mju.mjuton.cafe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mju.mjuton.global.ApiException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

@Component
class KakaoCafeSearchClient implements CafeSearchClient {
	private static final int MIN_RADIUS_METERS = 1;
	private static final int MAX_RADIUS_METERS = 20_000;
	private static final int MIN_SIZE = 1;
	private static final int MAX_SIZE = 15;
	private final RestClient restClient;
	private final String apiKey;
	private final int radiusMeters;
	private final int size;

	KakaoCafeSearchClient(@Value("${mjuton.cafe-search.kakao-api-key}") String apiKey,
			@Value("${mjuton.cafe-search.radius-meters}") int radiusMeters,
			@Value("${mjuton.cafe-search.size}") int size) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		validateConfig(radiusMeters, size);
		this.radiusMeters = radiusMeters;
		this.size = size;
	}

	private void validateConfig(int radiusMeters, int size) {
		if (radiusMeters < MIN_RADIUS_METERS || radiusMeters > MAX_RADIUS_METERS) {
			throw new IllegalArgumentException("mjuton.cafe-search.radius-meters must be between 1 and 20000");
		}
		if (size < MIN_SIZE || size > MAX_SIZE) {
			throw new IllegalArgumentException("mjuton.cafe-search.size must be between 1 and 15");
		}
	}

	@Override
	public List<CafeCandidate> searchNearby(double latitude, double longitude) {
		if (apiKey.isEmpty()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAFE_SEARCH_UNAVAILABLE",
					"카페 검색 API 키가 설정되어 있지 않습니다.");
		}
		try {
			JsonNode response = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.scheme("https")
							.host("dapi.kakao.com")
							.path("/v2/local/search/keyword.json")
							.queryParam("query", "카페")
							.queryParam("x", longitude)
							.queryParam("y", latitude)
							.queryParam("radius", radiusMeters)
							.queryParam("size", size)
							.build())
					.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
					.retrieve()
					.body(JsonNode.class);
			return parseDocuments(response);
		} catch (RestClientException exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAFE_SEARCH_FAILED",
					"외부 카페 검색 API 호출에 실패했습니다.");
		}
	}

	List<CafeCandidate> parseDocuments(JsonNode response) {
		List<CafeCandidate> candidates = new ArrayList<>();
		JsonNode documents = response == null ? null : response.get("documents");
		if (documents == null || !documents.isArray()) return candidates;
		for (JsonNode document : documents) {
			String name = text(document, "place_name");
			Double latitude = number(document, "y");
			Double longitude = number(document, "x");
			if (name == null || latitude == null || longitude == null
					|| !Double.isFinite(latitude) || !Double.isFinite(longitude)
					|| latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
				continue;
			}
			candidates.add(new CafeCandidate(
					name,
					latitude,
					longitude,
					text(document, "road_address_name", text(document, "address_name")),
					text(document, "phone"),
					null,
					"외부 카페 검색 결과에 주차 정보가 없어 확인이 필요합니다."));
		}
		return candidates;
	}

	private String text(JsonNode node, String field) {
		return text(node, field, null);
	}

	private String text(JsonNode node, String field, String defaultValue) {
		JsonNode value = node.get(field);
		if (value == null || value.asText().isBlank()) return defaultValue;
		return value.asText();
	}

	private Double number(JsonNode node, String field) {
		String value = text(node, field);
		if (value == null) return null;
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException exception) {
			return null;
		}
	}
}
