package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.StudyGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
	List<StudyGroup> findAllByOrderByCreatedAtDescIdDesc();

	@EntityGraph(attributePaths = "recruitingRoles")
	Optional<StudyGroup> findWithRecruitingRolesById(Long id);
}
