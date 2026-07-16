package com.mju.mjuton.recruitment.repository;

import com.mju.mjuton.recruitment.domain.Recruitment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruitmentRepository extends JpaRepository<Recruitment, Long> {
	/** 목록 화면 — 최신 모집글부터. */
	List<Recruitment> findAllByOrderByIdDesc();
}
