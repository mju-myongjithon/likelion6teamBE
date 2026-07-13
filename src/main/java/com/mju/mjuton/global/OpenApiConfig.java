package com.mju.mjuton.global;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
		title = "CampusLink API",
		description = "명지대학교 학생을 위한 스터디·해커톤 팀 구성 서비스 API",
		version = "v1"
))
@SecurityScheme(
		name = OpenApiConfig.SESSION_COOKIE,
		description = "회원가입 또는 로그인 성공 시 발급되는 서버 세션 쿠키",
		type = SecuritySchemeType.APIKEY,
		in = SecuritySchemeIn.COOKIE,
		paramName = "JSESSIONID"
)
public class OpenApiConfig {
	public static final String SESSION_COOKIE = "sessionCookie";
}
