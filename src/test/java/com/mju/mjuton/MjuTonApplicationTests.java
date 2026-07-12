package com.mju.mjuton;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

}
