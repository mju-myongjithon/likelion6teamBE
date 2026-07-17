package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.GroupJoinApplication;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupJoinApplicationRepository extends JpaRepository<GroupJoinApplication, Long> {
	Optional<GroupJoinApplication> findByGroup_IdAndApplicant_Id(Long groupId, Long applicantId);
	List<GroupJoinApplication> findByGroup_IdAndStatusOrderByRequestedAtAscIdAsc(
			Long groupId, GroupJoinApplicationStatus status);

	@EntityGraph(attributePaths = {"group", "group.leader"})
	Page<GroupJoinApplication> findByApplicant_Id(Long applicantId, Pageable pageable);

	@EntityGraph(attributePaths = {"group", "group.leader"})
	Page<GroupJoinApplication> findByApplicant_IdAndStatus(
			Long applicantId, GroupJoinApplicationStatus status, Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select application from GroupJoinApplication application where application.id = :id")
	Optional<GroupJoinApplication> findByIdForUpdate(@Param("id") Long id);
}
