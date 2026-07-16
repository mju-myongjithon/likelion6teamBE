package com.mju.mjuton.recruitment.repository;

import com.mju.mjuton.recruitment.domain.JoinRequest;
import com.mju.mjuton.recruitment.domain.JoinRequestStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, Long> {
	/** 중복 대기 신청 방지용 — 같은 사람이 이미 PENDING인지 확인. */
	boolean existsByRecruitmentIdAndApplicantIdAndStatus(Long recruitmentId, Long applicantId, JoinRequestStatus status);
	/** 방장이 볼 대기 중인 신청 목록. */
	List<JoinRequest> findByRecruitmentIdAndStatusOrderByIdAsc(Long recruitmentId, JoinRequestStatus status);
}
