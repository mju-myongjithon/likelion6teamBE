package com.mju.mjuton.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mju.mjuton.recommendation.service.OpenAiRecommendationClient;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.AiAssessment;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateInput;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateKey;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.ProfileInput;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OpenAiRecommendationClientTests {
	private HttpServer server;

	@AfterEach
	void stopServer() {
		if (server != null) server.stop(0);
	}

	@Test
	void sendsResponsesStructuredOutputRequestAndParsesOnlyAllowedCandidates() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		AtomicReference<String> capturedBody = new AtomicReference<>();
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/v1/responses", exchange -> {
			capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String output = """
					{"recommendations":[
					  {"category":"STUDY","targetId":7,"aiScore":88,"reasons":["프로필과 의미적으로 잘 맞아요."]},
					  {"category":"STUDY","targetId":999,"aiScore":100,"reasons":["허용되지 않은 후보"]}
					]}
					""";
			String response = mapper.writeValueAsString(Map.of("output", List.of(Map.of(
					"type", "message",
					"content", List.of(Map.of("type", "output_text", "text", output))))));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		OpenAiRecommendationClient client = new OpenAiRecommendationClient(mapper, "test-key", "test-model",
				"http://localhost:" + server.getAddress().getPort());
		CandidateKey key = new CandidateKey("STUDY", 7L);
		ProfileInput profile = new ProfileInput("명지대학교", "컴퓨터공학과", "서울", "백엔드 개발",
				List.of("Spring"), List.of("스터디"), List.of("백엔드"));
		CandidateInput candidate = new CandidateInput(key, "Spring 스터디", "백엔드 프로젝트",
				"서울", List.of("백엔드 / Spring"), 80);

		Map<CandidateKey, AiAssessment> result = client.assess(profile, List.of(candidate));

		assertEquals(1, result.size());
		assertEquals(88, result.get(key).score());
		assertEquals(List.of("프로필과 의미적으로 잘 맞아요."), result.get(key).reasons());
		JsonNode request = mapper.readTree(capturedBody.get());
		assertEquals("test-model", request.path("model").asString());
		assertTrue(request.path("input").isString());
		assertEquals("json_schema", request.path("text").path("format").path("type").asString());
		assertTrue(request.path("text").path("format").path("strict").asBoolean());
	}

	@Test
	void missingKeyAndHttpFailureReturnEmptyFallbackResult() {
		ObjectMapper mapper = new ObjectMapper();
		ProfileInput profile = new ProfileInput("학교", "학과", "서울", null, List.of(), List.of(), List.of());
		CandidateInput candidate = new CandidateInput(new CandidateKey("STUDY", 1L),
				"스터디", "소개", "서울", List.of(), 10);

		assertTrue(new OpenAiRecommendationClient(mapper, "", "model", "http://localhost")
				.assess(profile, List.of(candidate)).isEmpty());
		assertTrue(new OpenAiRecommendationClient(mapper, "key", "model", "http://127.0.0.1:1")
				.assess(profile, List.of(candidate)).isEmpty());
	}
}
