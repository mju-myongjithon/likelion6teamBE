package com.mju.mjuton.cafe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mju.mjuton.global.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoResidenceCoordinateResolverTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void searchesDistrictOfficeAndReturnsItsCoordinate() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(builder.build(), "test-api-key");
		server.expect(requestTo(containsString("query=%EC%84%9C%EC%9A%B8%ED%8A%B9%EB%B3%84%EC%8B%9C%20%EA%B0%95%EB%82%A8%EA%B5%AC%EC%B2%AD")))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-api-key"))
				.andRespond(withSuccess("""
						{
						  "documents": [
						    {
						      "place_name": "강남구청",
						      "address_name": "서울 강남구 삼성동 16-1",
						      "x": "127.0473",
						      "y": "37.5172"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		var coordinate = resolver.resolve(" 서울특별시 · 강남구 ");

		assertThat(coordinate).isPresent();
		assertThat(coordinate.orElseThrow().latitude()).isEqualTo(37.5172);
		assertThat(coordinate.orElseThrow().longitude()).isEqualTo(127.0473);
		server.verify();
	}

	@Test
	void usesCityHallForMetropolitanCityShortName() {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		assertThat(resolver.officeQuery("서울")).isEqualTo("서울특별시청");
		assertThat(resolver.officeQuery("부산")).isEqualTo("부산광역시청");
		assertThat(resolver.officeQuery("세종")).isEqualTo("세종특별자치시청");
		assertThat(resolver.officeQuery("경기도 수원시")).isEqualTo("경기도 수원시청");
		assertThat(resolver.officeQuery("서울특별시 강남구")).isEqualTo("서울특별시 강남구청");
		assertThat(resolver.officeQuery("강원특별자치도 양양군")).isEqualTo("강원특별자치도 양양군청");
		assertThat(resolver.officeQuery("용인시 처인구 역북동 587-1")).isEqualTo("용인시 처인구청");
		assertThat(resolver.officeQuery("경기도 용인시 처인구 역북동 587-1"))
				.isEqualTo("경기도 용인시 처인구청");
	}

	@Test
	void matchesOfficialCityHallNamesForShortAreasAndUsesUpperAddressForGwangju() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		assertThat(resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"서울특별시청","address_name":"서울 중구","x":"126.9780","y":"37.5665"}
				]}
				"""), "서울", "서울특별시청")).isPresent();
		assertThat(resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"부산광역시청","address_name":"부산 연제구","x":"129.0756","y":"35.1796"}
				]}
				"""), "부산", "부산광역시청")).isPresent();
		assertThat(resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"세종특별자치시청","address_name":"세종 보람동","x":"127.2890","y":"36.4800"}
				]}
				"""), "세종", "세종특별자치시청")).isPresent();

		var gwangju = resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"광주광역시청","address_name":"경기 광주시 송정동","x":"127.2550","y":"37.4294"},
				  {"place_name":"광주광역시청","address_name":"광주 서구 치평동","x":"126.8514","y":"35.1601"}
				]}
				"""), "광주", "광주광역시청");
		assertThat(gwangju).isPresent();
		assertThat(gwangju.orElseThrow().latitude()).isEqualTo(35.1601);
	}

	@Test
	void acceptsRenamedMetropolitanOfficeWithoutAcceptingAnotherGwangju() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		var gwangju = resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"광주시청","address_name":"경기 광주시 송정동","x":"127.2550","y":"37.4294"},
				  {"place_name":"전남광주통합특별시청 광주청사","address_name":"전남광주통합특별시 서구 치평동 1200","x":"126.85162995901466","y":"35.16010195999625"}
				]}
				"""), "광주", "광주광역시청");

		assertThat(gwangju).isPresent();
		assertThat(gwangju.orElseThrow().latitude()).isEqualTo(35.16010195999625);
		assertThat(gwangju.orElseThrow().longitude()).isEqualTo(126.85162995901466);
		assertThat(resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"전남광주통합특별시청 광주청사","address_name":"전남광주통합특별시 서구 치평동 1200","x":"126.85162995901466","y":"35.16010195999625"}
				]}
				"""), "광주광역시", "광주광역시청")).isPresent();
	}

	@Test
	void matchesCountyOfficeCandidate() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		var coordinate = resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"양양군청","address_name":"강원 양양군 양양읍","x":"128.6191","y":"38.0754"}
				]}
				"""), "강원특별자치도 양양군", "양양군청");

		assertThat(coordinate).isPresent();
	}

	@Test
	void matchesDistrictOfficeForDetailedAddressWithoutProvince() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		var coordinate = resolver.coordinate(objectMapper.readTree("""
				{"documents":[
				  {"place_name":"처인구청","address_name":"경기 용인시 처인구 김량장동 286","x":"127.201357139726","y":"37.2343060386946"}
				]}
				"""), "용인시 처인구 역북동 587-1", "처인구청");

		assertThat(coordinate).isPresent();
		assertThat(coordinate.orElseThrow().latitude()).isEqualTo(37.2343060386946);
		assertThat(coordinate.orElseThrow().longitude()).isEqualTo(127.201357139726);
	}

	@Test
	void ignoresWrongOfficeUpperAreaAndInvalidCoordinates() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		var coordinate = resolver.coordinate(objectMapper.readTree("""
				{
				  "documents": [
				    {
				      "place_name": "강남구청",
				      "address_name": "부산 강남구",
				      "x": "127.0473",
				      "y": "37.5172"
				    },
				    {
				      "place_name": "강남구청",
				      "address_name": "서울 강남구",
				      "x": "200",
				      "y": "37.5172"
				    }
				  ]
				}
				"""), "서울특별시 강남구", "강남구청");

		assertThat(coordinate).isEmpty();
	}

	@Test
	void emptyDocumentsReturnEmptyAndMissingKeyIsUnavailable() throws Exception {
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(RestClient.create(), "");

		assertThat(resolver.coordinate(objectMapper.readTree("{\"documents\":[]}"),
				"서울특별시 강남구", "강남구청")).isEmpty();
		assertThatThrownBy(() -> resolver.resolve("서울특별시 강남구"))
				.isInstanceOf(ApiException.class)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(exception.getCode()).isEqualTo("CAFE_SEARCH_UNAVAILABLE");
				});
	}

	@Test
	void externalSearchFailureIsServiceUnavailable() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoResidenceCoordinateResolver resolver =
				new KakaoResidenceCoordinateResolver(builder.build(), "test-api-key");
		server.expect(requestTo(containsString("/v2/local/search/keyword.json")))
				.andRespond(withServerError());

		assertThatThrownBy(() -> resolver.resolve("서울특별시 강남구"))
				.isInstanceOf(ApiException.class)
				.isInstanceOfSatisfying(ApiException.class, exception -> {
					assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(exception.getCode()).isEqualTo("CAFE_SEARCH_FAILED");
				});
		server.verify();
	}
}
