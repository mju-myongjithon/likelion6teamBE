package com.mju.mjuton.group.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupInquiry;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupInquiryRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupInquiryService {
	private static final int MAX_CONTENT_LENGTH = 1000;

	private final GroupInquiryRepository inquiries;
	private final StudyGroupRepository groups;
	private final UserRepository users;

	public GroupInquiryService(GroupInquiryRepository inquiries, StudyGroupRepository groups,
			UserRepository users) {
		this.inquiries = inquiries;
		this.groups = groups;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public InquiryPageResponse findAll(long groupId, Long requesterUserId, int page, int size) {
		StudyGroup group = findGroup(groupId);
		validatePage(page, size);
		PageRequest pageable = PageRequest.of(page, size,
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<GroupInquiry> found = inquiries.findByGroup_Id(groupId, pageable);
		List<InquiryResponse> content = found.getContent().stream()
				.map(inquiry -> InquiryResponse.from(inquiry, group.getLeaderUserId(), requesterUserId))
				.toList();
		return new InquiryPageResponse(content, found.getNumber(), found.getSize(),
				found.getTotalElements(), found.getTotalPages(), found.isFirst(), found.isLast());
	}

	@Transactional
	public InquiryResponse create(long groupId, long authorUserId, String content) {
		StudyGroup group = findGroup(groupId);
		User author = findUser(authorUserId);
		GroupInquiry inquiry = inquiries.saveAndFlush(
				new GroupInquiry(group, author, normalize(content, "문의 내용")));
		return InquiryResponse.from(inquiry, group.getLeaderUserId(), authorUserId);
	}

	@Transactional
	public void delete(long groupId, long inquiryId, long requesterUserId) {
		findUser(requesterUserId);
		findGroup(groupId);
		GroupInquiry inquiry = inquiries.findByIdAndGroup_Id(inquiryId, groupId)
				.orElseThrow(GroupInquiryService::inquiryNotFound);
		if (!Objects.equals(inquiry.getAuthorUserId(), requesterUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "INQUIRY_AUTHOR_REQUIRED",
					"문의 작성자만 삭제할 수 있습니다.");
		}
		inquiries.delete(inquiry);
	}

	@Transactional
	public InquiryResponse answer(long groupId, long inquiryId, long leaderUserId, String content) {
		findUser(leaderUserId);
		StudyGroup group = groups.findByIdForUpdate(groupId)
				.orElseThrow(GroupInquiryService::groupNotFound);
		if (!Objects.equals(group.getLeaderUserId(), leaderUserId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_LEADER_REQUIRED", "모임 리더만 답변할 수 있습니다.");
		}
		String normalizedContent = normalize(content, "답변 내용");
		GroupInquiry inquiry = inquiries.findByIdAndGroupIdForUpdate(inquiryId, groupId)
				.orElseThrow(GroupInquiryService::inquiryNotFound);
		if (inquiry.isAnswered()) {
			throw new ApiException(HttpStatus.CONFLICT, "INQUIRY_ALREADY_ANSWERED", "이미 답변된 문의입니다.");
		}
		inquiry.answer(normalizedContent);
		return InquiryResponse.from(inquiry, group.getLeaderUserId(), leaderUserId);
	}

	private StudyGroup findGroup(long groupId) {
		return groups.findById(groupId).orElseThrow(GroupInquiryService::groupNotFound);
	}

	private User findUser(long userId) {
		return users.findById(userId).orElseThrow(GroupInquiryService::authenticationRequired);
	}

	private String normalize(String value, String field) {
		if (value == null) throw invalidRequest(field + "은(는) 필수입니다.");
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
			throw invalidRequest(field + "은(는) 1~" + MAX_CONTENT_LENGTH + "자여야 합니다.");
		}
		return normalized;
	}

	private void validatePage(int page, int size) {
		if (page < 0) throw invalidRequest("page는 0 이상이어야 합니다.");
		if (size < 1 || size > 100) throw invalidRequest("size는 1~100이어야 합니다.");
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException groupNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "모임이 존재하지 않습니다.");
	}

	private static ApiException inquiryNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "GROUP_INQUIRY_NOT_FOUND", "모임 문의가 존재하지 않습니다.");
	}

	@Schema(name = "GroupInquiryPageResponse", description = "모임 문의 페이지")
	public record InquiryPageResponse(List<InquiryResponse> content, int page, int size,
			long totalElements, int totalPages, boolean first, boolean last) {}

	@Schema(name = "GroupInquiryResponse", description = "모임 문의와 답변")
	public record InquiryResponse(Long inquiryId, String content, Instant createdAt, AnswerResponse answer,
			boolean canDelete, boolean canAnswer) {
		static InquiryResponse from(GroupInquiry inquiry, long leaderUserId, Long requesterUserId) {
			boolean answered = inquiry.isAnswered();
			return new InquiryResponse(inquiry.getId(), inquiry.getContent(), inquiry.getCreatedAt(),
					answered ? new AnswerResponse(inquiry.getAnswerContent(), inquiry.getAnsweredAt()) : null,
					Objects.equals(inquiry.getAuthorUserId(), requesterUserId),
					Objects.equals(leaderUserId, requesterUserId) && !answered);
		}
	}

	@Schema(name = "GroupInquiryAnswerResponse", description = "모임 문의 답변")
	public record AnswerResponse(String content, Instant answeredAt) {}
}
