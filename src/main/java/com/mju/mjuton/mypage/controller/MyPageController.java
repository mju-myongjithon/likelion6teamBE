package com.mju.mjuton.mypage.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.mypage.service.MyPageService;
import com.mju.mjuton.mypage.service.MyPageService.MyPageSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
@Tag(name = "마이페이지", description = "현재 사용자의 참여 통계, 월별 약속, 신청 행사와 내 모임을 조회합니다.")
@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
public class MyPageController {
	private final MyPageService myPageService;

	public MyPageController(MyPageService myPageService) {
		this.myPageService = myPageService;
	}

	@GetMapping
	@Operation(summary = "마이페이지 통계, 월별 활동, 신청 행사 및 내 모임 조회")
	MyPageSummary find(@RequestParam int year, @RequestParam int month, HttpServletRequest request) {
		try {
			return myPageService.find(sessionUserId(request), YearMonth.of(year, month));
		} catch (DateTimeException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "유효한 연도와 월을 입력해 주세요.");
		}
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}
}
