package com.mju.mjuton.global;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "시스템", description = "애플리케이션 상태를 확인합니다.")
public class HealthCheckController {

	@GetMapping("/api/health")
	@Operation(summary = "헬스 체크", description = "애플리케이션이 HTTP 요청을 처리할 수 있는지 확인합니다.")
	@ApiResponse(responseCode = "200", description = "애플리케이션 응답 정상",
			content = @Content(schema = @Schema(implementation = HealthCheckResponse.class)))
	public HealthCheckResponse health() {
		return new HealthCheckResponse("ok", "mju-ton");
	}

	@Schema(name = "HealthCheckResponse", description = "애플리케이션 상태")
	public record HealthCheckResponse(
			@Schema(description = "서비스 상태", example = "ok") String status,
			@Schema(description = "서비스 이름", example = "mju-ton") String service) {
	}
}
