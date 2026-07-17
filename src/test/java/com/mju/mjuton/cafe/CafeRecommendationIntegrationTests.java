package com.mju.mjuton.cafe;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mju.mjuton.auth.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CafeRecommendationIntegrationTests {
	@Autowired MockMvc mvc;

	@Test
	void recommendsTopThreeCafesByMinimumTotalDistanceFromUserLocations() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 2, "latitude": 37.2220, "longitude": 127.1880},
						    {"userId": 3, "latitude": 37.2240, "longitude": 127.1900},
						    {"userId": 4, "latitude": 37.2250, "longitude": 127.1910}
						  ],
						  "cafes": [
						    {
						      "name": "가까운 중앙 카페",
						      "latitude": 37.2230,
						      "longitude": 127.1888,
						      "address": "명지대 앞",
						      "phone": "031-000-0000",
						      "parkingAvailable": true,
						      "parkingInfo": "건물 뒤편 주차 가능"
						    },
						    {
						      "name": "두 번째 카페",
						      "latitude": 37.2240,
						      "longitude": 127.1895,
						      "address": "명지대 근처",
						      "parkingAvailable": null,
						      "parkingInfo": "확인 필요"
						    },
						    {
						      "name": "세 번째 카페",
						      "latitude": 37.2250,
						      "longitude": 127.1910,
						      "address": "명지대 사거리",
						      "parkingAvailable": false,
						      "parkingInfo": "주차 불가"
						    },
						    {
						      "name": "먼 카페",
						      "latitude": 37.2500,
						      "longitude": 127.2200,
						      "address": "먼 주소",
						      "parkingAvailable": false,
						      "parkingInfo": "주차 불가"
						    }
						  ]
						}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendations.length()").value(3))
				.andExpect(jsonPath("$.recommendations[0].rank").value(1))
				.andExpect(jsonPath("$.recommendations[0].location.placeName").value("가까운 중앙 카페"))
				.andExpect(jsonPath("$.recommendations[0].location.latitude").value(37.2230))
				.andExpect(jsonPath("$.recommendations[0].location.longitude").value(127.1888))
				.andExpect(jsonPath("$.recommendations[0].detail.parkingAvailable").value(true))
				.andExpect(jsonPath("$.recommendations[0].detail.parkingInfo").value("건물 뒤편 주차 가능"))
				.andExpect(jsonPath("$.recommendations[0].reason", containsString("직선거리 기준")))
				.andExpect(jsonPath("$.recommendations[0].reason", containsString("1순위")))
				.andExpect(jsonPath("$.recommendations[1].rank").value(2))
				.andExpect(jsonPath("$.recommendations[2].rank").value(3));
	}

	@Test
	void missingCafeSearchApiKeyReturnsServiceUnavailableWhenNoCandidatesAreProvided() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 2, "latitude": 37.2220, "longitude": 127.1880}
						  ]
						}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CAFE_SEARCH_UNAVAILABLE"));
	}

	@Test
	void requiresAtLeastTwoUserLocations() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860}
						  ],
						  "cafes": []
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void explicitEmptyCafeCandidatesDoNotTriggerExternalSearch() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 2, "latitude": 37.2220, "longitude": 127.1880}
						  ],
						  "cafes": []
						}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CAFE_NOT_FOUND"));
	}

	@Test
	void missingCoordinateAndNullListElementReturnInvalidRequest() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 2, "latitude": 37.2220}
						  ],
						  "cafes": [
						    {
						      "name": "중앙 카페",
						      "latitude": 37.2230,
						      "longitude": 127.1888
						    }
						  ]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    null
						  ],
						  "cafes": [
						    {
						      "name": "중앙 카페",
						      "latitude": 37.2230,
						      "longitude": 127.1888
						    }
						  ]
						}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void nullRequestBodyReturnsInvalidRequest() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("null"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void recommendationRequiresSession() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "users": [
						    {"userId": 1, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 2, "latitude": 37.2220, "longitude": 127.1880}
						  ],
						  "cafes": []
						}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}

	@Test
	void requesterMustBeIncludedWhenUserIdsAreProvided() throws Exception {
		mvc.perform(post("/api/cafes/recommendations").contentType(MediaType.APPLICATION_JSON)
				.session(session())
				.content("""
						{
						  "users": [
						    {"userId": 2, "latitude": 37.2210, "longitude": 127.1860},
						    {"userId": 3, "latitude": 37.2220, "longitude": 127.1880}
						  ],
						  "cafes": [
						    {
						      "name": "중앙 카페",
						      "latitude": 37.2230,
						      "longitude": 127.1888
						    }
						  ]
						}
						"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CAFE_RECOMMENDATION_FORBIDDEN"));
	}

	private MockHttpSession session() {
		MockHttpSession session = new MockHttpSession();
		session.setAttribute(AuthController.SESSION_USER_ID, 1L);
		return session;
	}
}
