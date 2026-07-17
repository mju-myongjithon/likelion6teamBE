package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.StudyGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
	List<StudyGroup> findAllByOrderByCreatedAtDescIdDesc();

	@EntityGraph(attributePaths = "recruitingRoles")
	Optional<StudyGroup> findWithRecruitingRolesById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select studyGroup from StudyGroup studyGroup where studyGroup.id = :id")
	Optional<StudyGroup> findByIdForUpdate(@Param("id") Long id);
}
