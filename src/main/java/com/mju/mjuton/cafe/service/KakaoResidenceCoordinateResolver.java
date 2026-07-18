package com.mju.mjuton.cafe.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mju.mjuton.global.ApiException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
class KakaoResidenceCoordinateResolver implements ResidenceCoordinateResolver {
	private static final Map<String, String> CITY_HALL_NAMES = Map.ofEntries(
			Map.entry("서울", "서울특별시청"),
			Map.entry("부산", "부산광역시청"),
			Map.entry("대구", "대구광역시청"),
			Map.entry("인천", "인천광역시청"),
			Map.entry("광주", "광주광역시청"),
			Map.entry("대전", "대전광역시청"),
			Map.entry("울산", "울산광역시청"),
			Map.entry("세종", "세종특별자치시청"));

	private final RestClient restClient;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String apiKey;

	@Autowired
	KakaoResidenceCoordinateResolver(@Value("${mjuton.cafe-search.kakao-api-key}") String apiKey) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(3));
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	KakaoResidenceCoordinateResolver(RestClient restClient, String apiKey) {
		this.restClient = restClient;
		this.apiKey = apiKey == null ? "" : apiKey.trim();
	}

	@Override
	public Optional<Coordinate> resolve(String residenceArea) {
		String normalizedArea = normalizeArea(residenceArea);
		if (normalizedArea == null) return Optional.empty();
		if (apiKey.isEmpty()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAFE_SEARCH_UNAVAILABLE",
					"카페 검색 API 키가 설정되어 있지 않습니다.");
		}
		String query = officeQuery(normalizedArea);
		try {
			String responseBody = restClient.get()
					.uri(uriBuilder -> uriBuilder
							.scheme("https")
							.host("dapi.kakao.com")
							.path("/v2/local/search/keyword.json")
							.queryParam("query", query)
							.queryParam("size", 15)
							.build())
					.header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
					.retrieve()
					.body(String.class);
			JsonNode response = responseBody == null ? null : objectMapper.readTree(responseBody);
			return coordinate(response, normalizedArea, expectedOfficeName(query));
		} catch (RestClientException | JsonProcessingException exception) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAFE_SEARCH_FAILED",
					"외부 위치 검색 API 호출에 실패했습니다.");
		}
	}

	String officeQuery(String residenceArea) {
		List<String> areaTokens = List.of(residenceArea.split(" "));
		int officeAreaIndex = -1;
		for (int index = 0; index < areaTokens.size(); index++) {
			if (isAdministrativeOfficeArea(areaTokens.get(index))) officeAreaIndex = index;
		}
		if (officeAreaIndex >= 0) {
			return String.join(" ", areaTokens.subList(0, officeAreaIndex + 1)) + "청";
		}
		return CITY_HALL_NAMES.getOrDefault(residenceArea, residenceArea + " 시청");
	}

	private boolean isAdministrativeOfficeArea(String token) {
		return token.endsWith("구") || token.endsWith("군")
				|| token.endsWith("시") || token.endsWith("도");
	}

	Optional<Coordinate> coordinate(JsonNode response, String residenceArea, String expectedOfficeName) {
		JsonNode documents = response == null ? null : response.get("documents");
		if (documents == null || !documents.isArray()) return Optional.empty();
		for (JsonNode document : documents) {
			String placeName = text(document, "place_name");
			String address = text(document, "address_name", text(document, "road_address_name"));
			Double latitude = number(document, "y");
			Double longitude = number(document, "x");
			if (!matchesOffice(placeName, expectedOfficeName, residenceArea)
					|| !matchesUpperArea(address, residenceArea)
					|| !valid(latitude, longitude)) {
				continue;
			}
			return Optional.of(new Coordinate(latitude, longitude));
		}
		return Optional.empty();
	}

	private boolean matchesOffice(String placeName, String expectedOfficeName, String residenceArea) {
		if (placeName == null) return false;
		String compactPlaceName = compact(placeName);
		if (compactPlaceName.contains(compact(expectedOfficeName))) return true;
		String metropolitanStem = metropolitanStem(residenceArea);
		return metropolitanStem != null
				&& compactPlaceName.contains(compact(metropolitanStem))
				&& compactPlaceName.contains("시청");
	}

	private boolean matchesUpperArea(String address, String residenceArea) {
		List<String> areaTokens = List.of(residenceArea.split(" "));
		String firstAreaToken = areaTokens.get(0);
		boolean hasUpperArea = areaTokens.size() >= 2
				|| CITY_HALL_NAMES.containsKey(firstAreaToken)
				|| isUpperAdministrativeArea(firstAreaToken);
		if (!hasUpperArea) return true;
		if (address == null) return false;
		String firstAreaStem = administrativeStem(firstAreaToken);
		List<String> addressTokens = List.of(address.trim().split("\\s+"));
		if (metropolitanStem(residenceArea) != null) {
			return administrativeStem(addressTokens.get(0)).contains(firstAreaStem);
		}
		return addressTokens.stream()
				.map(this::administrativeStem)
				.anyMatch(firstAreaStem::equals);
	}

	private String metropolitanStem(String residenceArea) {
		if (CITY_HALL_NAMES.containsKey(residenceArea)) return residenceArea;
		if (residenceArea.contains(" ")) return null;
		if (residenceArea.endsWith("특별자치시") || residenceArea.endsWith("특별시")
				|| residenceArea.endsWith("광역시")) {
			return administrativeStem(residenceArea);
		}
		return null;
	}

	private boolean isUpperAdministrativeArea(String value) {
		return value.endsWith("특별자치도") || value.endsWith("특별자치시")
				|| value.endsWith("특별시") || value.endsWith("광역시") || value.endsWith("도");
	}

	private String administrativeStem(String value) {
		for (String suffix : List.of("특별자치도", "특별자치시", "특별시", "광역시", "도", "시")) {
			if (value.endsWith(suffix)) return value.substring(0, value.length() - suffix.length());
		}
		return value;
	}

	private String expectedOfficeName(String query) {
		return lastToken(query);
	}

	private String lastToken(String value) {
		int lastSpace = value.lastIndexOf(' ');
		return lastSpace < 0 ? value : value.substring(lastSpace + 1);
	}

	private String normalizeArea(String value) {
		if (value == null) return null;
		String normalized = value.trim().replace('·', ' ').replaceAll("\\s+", " ");
		return normalized.isEmpty() ? null : normalized;
	}

	private String compact(String value) {
		return value.replaceAll("\\s+", "");
	}

	private boolean valid(Double latitude, Double longitude) {
		return latitude != null && longitude != null
				&& Double.isFinite(latitude) && Double.isFinite(longitude)
				&& latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
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
