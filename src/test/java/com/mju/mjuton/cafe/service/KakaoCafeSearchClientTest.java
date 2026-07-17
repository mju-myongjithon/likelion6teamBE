package com.mju.mjuton.cafe.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
}
