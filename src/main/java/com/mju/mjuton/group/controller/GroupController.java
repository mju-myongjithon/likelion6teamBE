package com.mju.mjuton.group.controller;

import com.mju.mjuton.auth.controller.AuthController;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.global.ApiExceptionHandler.ErrorResponse;
import com.mju.mjuton.global.OpenApiConfig;
import com.mju.mjuton.group.domain.StudyGroup.RoleValues;
import com.mju.mjuton.group.service.GroupService;
import com.mju.mjuton.group.service.GroupService.GroupDetail;
import com.mju.mjuton.group.service.GroupService.GroupSummary;
import com.mju.mjuton.group.service.GroupService.MyGroupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
@Tag(name = "스터디 모임", description = "스터디 모임을 등록·조회·수정·삭제합니다.")
public class GroupController {
	private final GroupService groupService;

	public GroupController(GroupService groupService) {
		this.groupService = groupService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "스터디 모임 등록", description = "세션 사용자를 리더로 하는 STUDY 모임을 등록합니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "모임 등록 성공"),
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	GroupDetail create(@Valid @RequestBody GroupRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return groupService.create(sessionUserId(request), body.toValues());
	}

	@GetMapping
	@Operation(summary = "스터디 모임 목록 조회", description = "최신 등록순으로 공개 목록을 조회합니다.")
	List<GroupSummary> findAll() {
		return groupService.findAll();
	}

	@GetMapping("/me")
	@Operation(summary = "내 모임 목록 조회", description = "리더이거나 승인된 참여자인 모임을 최신 등록순으로 조회합니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "내 모임 목록 조회 성공",
					content = @Content(array = @ArraySchema(schema = @Schema(implementation = MyGroupResponse.class)))),
			@ApiResponse(responseCode = "401", description = "로그인 필요",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	List<MyGroupResponse> findMine(@Parameter(hidden = true) HttpServletRequest request) {
		return groupService.findMine(sessionUserId(request));
	}

	@GetMapping("/{groupId}")
	@Operation(summary = "스터디 모임 상세 조회")
	@ApiResponse(responseCode = "404", description = "모임 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	GroupDetail find(@PathVariable long groupId) {
		return groupService.find(groupId);
	}

	@PutMapping("/{groupId}")
	@Operation(summary = "스터디 모임 전체 수정", description = "모임 리더만 모집 역할을 포함한 모든 필드를 교체할 수 있습니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	@ApiResponses({
			@ApiResponse(responseCode = "400", description = "요청값 규칙 위반", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "리더가 아님", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "모임 없음", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	GroupDetail update(@PathVariable long groupId, @Valid @RequestBody GroupRequest body,
			@Parameter(hidden = true) HttpServletRequest request) {
		return groupService.update(sessionUserId(request), groupId, body.toValues());
	}

	@DeleteMapping("/{groupId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "스터디 모임 삭제", description = "모임 리더만 삭제할 수 있습니다.")
	@SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE)
	void delete(@PathVariable long groupId, @Parameter(hidden = true) HttpServletRequest request) {
		groupService.delete(sessionUserId(request), groupId);
	}

	private long sessionUserId(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session == null || !(session.getAttribute(AuthController.SESSION_USER_ID) instanceof Long userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
		}
		return userId;
	}

	@Schema(name = "GroupRequest", description = "스터디 모임 등록·수정 요청")
	public record GroupRequest(
			@Schema(example = "주말 알고리즘 스터디", minLength = 1, maxLength = 100,
					requiredMode = Schema.RequiredMode.REQUIRED) String title,
			@Schema(example = "매주 문제를 풀고 풀이를 공유합니다.", minLength = 1, maxLength = 2000,
					requiredMode = Schema.RequiredMode.REQUIRED) String description,
			@Schema(description = "리더를 포함한 총 정원", example = "8", minimum = "1", maximum = "100",
					requiredMode = Schema.RequiredMode.REQUIRED) Integer maxMemberCount,
			@Schema(example = "매주 토요일 오후 2시", minLength = 1, maxLength = 1000,
					requiredMode = Schema.RequiredMode.REQUIRED) String meetingRule,
			@Schema(example = "강남", minLength = 1, maxLength = 200,
					requiredMode = Schema.RequiredMode.REQUIRED) String location,
			@ArraySchema(arraySchema = @Schema(description = "모집 역할과 요구 기술",
					requiredMode = Schema.RequiredMode.REQUIRED),
					schema = @Schema(implementation = RecruitingRoleRequest.class), maxItems = 20)
			@NotNull(message = "모집 역할 배열은 필수입니다.") List<RecruitingRoleRequest> recruitingRoles) {
		GroupService.GroupValues toValues() {
			return new GroupService.GroupValues(title, description, maxMemberCount, meetingRule, location,
					recruitingRoles == null ? null : recruitingRoles.stream().map(RecruitingRoleRequest::toValues).toList());
		}
	}

	public record RecruitingRoleRequest(
			@Schema(example = "프론트엔드", minLength = 1, maxLength = 50,
					requiredMode = Schema.RequiredMode.REQUIRED) String role,
			@Schema(example = "React", nullable = true, minLength = 1, maxLength = 100) String skill) {
		RoleValues toValues() { return new RoleValues(role, skill); }
	}
}
