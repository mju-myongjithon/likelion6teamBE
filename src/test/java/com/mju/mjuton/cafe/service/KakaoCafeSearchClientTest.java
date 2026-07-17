package com.mju.mjuton.cafe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoCafeSearchClientTest {
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void parsesOnlyDocumentsWithValidNameAndCoordinates() throws Exception {
		KakaoCafeSearchClient client = new KakaoCafeSearchClient("", 2000, 15);

		var candidates = client.parseDocuments(objectMapper.readTree("""
				{
				  "documents": [
				    {
				      "place_name": "정상 카페",
				      "x": "127.1888",
				      "y": "37.2230",
				      "road_address_name": "명지대 앞",
				      "phone": "031-000-0000"
				    },
				    {
				      "place_name": "좌표 누락 카페",
				      "x": "",
				      "y": "37.2230"
				    },
				    {
				      "place_name": "",
				      "x": "127.1888",
				      "y": "37.2230"
				    },
				    {
				      "place_name": "범위 오류 카페",
				      "x": "200",
				      "y": "37.2230"
				    }
				  ]
				}
				"""));

		assertThat(candidates).hasSize(1);
		assertThat(candidates.get(0).name()).isEqualTo("정상 카페");
		assertThat(candidates.get(0).latitude()).isEqualTo(37.2230);
		assertThat(candidates.get(0).longitude()).isEqualTo(127.1888);
		assertThat(candidates.get(0).parkingAvailable()).isNull();
		assertThat(candidates.get(0).parkingInfo()).contains("확인");
	}

	@Test
	void skipsNonFiniteCoordinates() throws Exception {
		KakaoCafeSearchClient client = new KakaoCafeSearchClient("", 2000, 15);

		var candidates = client.parseDocuments(objectMapper.readTree("""
				{
				  "documents": [
				    {
				      "place_name": "NaN 카페",
				      "x": "127.1888",
				      "y": "NaN"
				    },
				    {
				      "place_name": "무한 카페",
				      "x": "Infinity",
				      "y": "37.2230"
				    },
				    {
				      "place_name": "정상 카페",
				      "x": "127.1888",
				      "y": "37.2230"
				    }
				  ]
				}
				"""));

		assertThat(candidates).hasSize(1);
		assertThat(candidates.get(0).name()).isEqualTo("정상 카페");
	}

	@Test
	void rejectsInvalidSearchConfiguration() {
		assertThatThrownBy(() -> new KakaoCafeSearchClient("", 0, 15))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("radius-meters");
		assertThatThrownBy(() -> new KakaoCafeSearchClient("", 2000, 16))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("size");
	}

	@Test
	void searchesNearbyCafesFromKakaoResponseBody() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		KakaoCafeSearchClient client = new KakaoCafeSearchClient(builder.build(), "test-api-key", 2000, 3);
		server.expect(requestTo(startsWith("https://dapi.kakao.com/v2/local/search/keyword.json")))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-api-key"))
				.andRespond(withSuccess("""
						{
						  "documents": [
						    {
						      "place_name": "실제 응답 카페",
						      "x": "126.9225",
						      "y": "37.4020",
						      "road_address_name": "경기 안양시",
						      "phone": "031-000-0000"
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		var candidates = client.searchNearby(37.4, 126.9);

		assertThat(candidates).hasSize(1);
		assertThat(candidates.get(0).name()).isEqualTo("실제 응답 카페");
		server.verify();
	}
}
