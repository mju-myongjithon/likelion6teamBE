package com.mju.mjuton.recruitment.service;

import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.recruitment.domain.JoinRequest;
import com.mju.mjuton.recruitment.domain.JoinRequestStatus;
import com.mju.mjuton.recruitment.domain.Recruitment;
import com.mju.mjuton.recruitment.repository.JoinRequestRepository;
import com.mju.mjuton.recruitment.repository.RecruitmentRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모집글 작성 -> 참가신청 -> 방장 승인/거절의 워크플로우.
 * chat 모듈에는 두 지점에서만 의존한다: 모집글 생성 시 채팅방 생성(createRoom),
 * 승인 시 채팅방 멤버 등록(addMember). chat은 recruitment를 전혀 알지 못한다.
 */
@Service
public class RecruitmentService {
	private final RecruitmentRepository recruitments;
	private final JoinRequestRepository joinRequests;
	private final ChatService chatService;

	public RecruitmentService(RecruitmentRepository recruitments, JoinRequestRepository joinRequests,
			ChatService chatService) {
		this.recruitments = recruitments;
		this.joinRequests = joinRequests;
		this.chatService = chatService;
	}

	/** 모집글 작성 — 전용 채팅방을 함께 만들고 작성자를 방장 겸 첫 멤버로 등록한다. */
	@Transactional
	public Recruitment create(Long authorId, String title, String description, int capacity) {
		Long chatRoomId = chatService.createRoom(authorId, title).roomId(); //chat으로 이어지는 부분
		return recruitments.saveAndFlush(new Recruitment(authorId, chatRoomId, title, description, capacity)); //DB에 생성요청 및 authorID를 방장으로 부여
	}

	@Transactional(readOnly = true)
	public List<Recruitment> findAll() {
		return recruitments.findAllByOrderByIdDesc();
	}

	@Transactional(readOnly = true)
	public Recruitment find(Long recruitmentId) {
		return recruitments.findById(recruitmentId).orElseThrow(RecruitmentService::recruitmentNotFound);
	}

	/** 방장이 직접 모집을 마감한다. */
	@Transactional
	public void close(Long recruitmentId, Long userId) {
		requireAuthor(recruitmentId, userId).close();
	}

	/**
	 * 참가신청. 이미 멤버(작성자 포함)이거나 이미 대기 중이면 거부한다.
	 * 거절 이력이 있어도 새 신청은 자유롭게 허용한다.
	 */
	@Transactional
	public JoinRequest apply(Long recruitmentId, Long applicantId) {
		Recruitment recruitment = find(recruitmentId);
		if (!recruitment.isRecruiting()) {
			throw new ApiException(HttpStatus.CONFLICT, "RECRUITMENT_CLOSED", "마감된 모집글입니다.");
		}
		if (recruitment.isAuthor(applicantId) || chatService.isMember(recruitment.getChatRoomId(), applicantId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "이미 참여 중인 모집글입니다.");
		}
		if (joinRequests.existsByRecruitmentIdAndApplicantIdAndStatus(recruitmentId, applicantId,
				JoinRequestStatus.PENDING)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_APPLIED", "이미 승인 대기 중인 신청이 있습니다.");
		}
		return joinRequests.saveAndFlush(new JoinRequest(recruitmentId, applicantId));
	}

	/** 방장만 조회하는 대기 중인 신청 목록. */
	@Transactional(readOnly = true)
	public List<JoinRequest> pendingRequests(Long recruitmentId, Long userId) {
		requireAuthor(recruitmentId, userId);
		return joinRequests.findByRecruitmentIdAndStatusOrderByIdAsc(recruitmentId, JoinRequestStatus.PENDING);
	}

	/** 승인 — 정원이 남아 있을 때만 신청자를 채팅방 멤버로 승격시킨다. */
	@Transactional
	public void approve(Long recruitmentId, Long requestId, Long userId) {
		Recruitment recruitment = requireAuthor(recruitmentId, userId);
		JoinRequest request = requirePendingRequest(recruitmentId, requestId);
		if (chatService.countMembers(recruitment.getChatRoomId()) >= recruitment.getCapacity()) {
			throw new ApiException(HttpStatus.CONFLICT, "CAPACITY_FULL", "정원이 가득 찼습니다.");
		}
		request.approve(Instant.now());
		chatService.addMember(recruitment.getChatRoomId(), recruitment.getAuthorId(), request.getApplicantId());
	}

	/** 거절 — 신청자는 이후 자유롭게 재신청할 수 있다. */
	@Transactional
	public void reject(Long recruitmentId, Long requestId, Long userId) {
		requireAuthor(recruitmentId, userId);
		requirePendingRequest(recruitmentId, requestId).reject(Instant.now());
	}

	/** 방장 양도 — 현재 방장이 채팅방 멤버인 다른 사용자에게 방장을 넘긴다. */
	@Transactional
	public void transferOwnership(Long recruitmentId, Long currentOwnerId, Long newOwnerId) {
		Recruitment recruitment = requireAuthor(recruitmentId, currentOwnerId);
		if (recruitment.isAuthor(newOwnerId)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_AUTHOR", "이미 방장인 사용자입니다.");
		}
		if (!chatService.isMember(recruitment.getChatRoomId(), newOwnerId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NEW_OWNER_NOT_MEMBER", "채팅방 멤버에게만 방장을 양도할 수 있습니다.");
		}
		recruitment.transferAuthorTo(newOwnerId);
	}

	/**
	 * 방 나가기. 방장은 먼저 다른 멤버에게 양도해야 나갈 수 있고(authorId가 비멤버를 가리키는 것을 방지),
	 * 일반 멤버는 채팅방 명단(ChatRoomMember)에서 제거된다.
	 */
	@Transactional
	public void leave(Long recruitmentId, Long userId) {
		Recruitment recruitment = find(recruitmentId);
		if (recruitment.isAuthor(userId)) {
			throw new ApiException(HttpStatus.CONFLICT, "OWNER_MUST_TRANSFER_FIRST",
					"방장은 먼저 다른 멤버에게 양도한 뒤 나갈 수 있습니다.");
		}
		chatService.removeMember(recruitment.getChatRoomId(), userId);
	}

	private Recruitment requireAuthor(Long recruitmentId, Long userId) {
		Recruitment recruitment = find(recruitmentId);
		if (!recruitment.isAuthor(userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "NOT_RECRUITMENT_AUTHOR", "모집글 작성자만 할 수 있습니다.");
		}
		return recruitment;
	}

	private JoinRequest requirePendingRequest(Long recruitmentId, Long requestId) {
		JoinRequest request = joinRequests.findById(requestId)
				.filter(found -> found.getRecruitmentId().equals(recruitmentId))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "JOIN_REQUEST_NOT_FOUND", "참가신청을 찾을 수 없습니다."));
		if (!request.isPending()) {
			throw new ApiException(HttpStatus.CONFLICT, "JOIN_REQUEST_NOT_PENDING", "이미 처리된 신청입니다.");
		}
		return request;
	}

	private static ApiException recruitmentNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "RECRUITMENT_NOT_FOUND", "모집글을 찾을 수 없습니다.");
	}
}
