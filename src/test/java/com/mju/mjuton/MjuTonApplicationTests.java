package com.mju.mjuton;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
class MjuTonApplicationTests {

	@Autowired
	private MockMvcTester mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthCheckReturnsOk() {
		mockMvc.get().uri("/api/health")
			.assertThat()
			.hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("ok");
	}

	@Test
	void corsAllowsConfiguredFrontendOriginWithCredentials() {
		MockMvcRequestBuilder request = mockMvc.options().uri("/api/auth/session")
				.header(HttpHeaders.ORIGIN, "http://localhost:5173")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());

		request.assertThat()
				.hasStatusOk()
				.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173")
				.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
	}

}
