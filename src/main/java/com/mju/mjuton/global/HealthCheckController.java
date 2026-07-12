package com.mju.mjuton.global;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthCheckController {

	@GetMapping("/api/health")
	public HealthCheckResponse health() {
		return new HealthCheckResponse("ok", "mju-ton");
	}

	public record HealthCheckResponse(String status, String service) {
	}
}
