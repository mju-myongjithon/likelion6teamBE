package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.StudyGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {
	@EntityGraph(attributePaths = {"leader", "recruitingRoles"})
	List<StudyGroup> findAllByOrderByCreatedAtDescIdDesc();

	@Query("""
			select studyGroup
			from StudyGroup studyGroup
			where studyGroup.event.id = :eventId
			order by studyGroup.createdAt desc, studyGroup.id desc
			""")
	List<StudyGroup> findByEventIdOrderByCreatedAtDescIdDesc(@Param("eventId") Long eventId);

	@Modifying
	@Query("update StudyGroup studyGroup set studyGroup.event = null where studyGroup.event.id = :eventId")
	int unlinkEvent(@Param("eventId") Long eventId);

	@Query("""
			select distinct studyGroup
			from StudyGroup studyGroup
			join fetch studyGroup.leader leader
			left join studyGroup.members member
			where leader.id = :userId or member.user.id = :userId
			order by studyGroup.createdAt desc, studyGroup.id desc
			""")
	List<StudyGroup> findMyGroups(@Param("userId") Long userId);

	@EntityGraph(attributePaths = "recruitingRoles")
	Optional<StudyGroup> findWithRecruitingRolesById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select studyGroup from StudyGroup studyGroup where studyGroup.id = :id")
	Optional<StudyGroup> findByIdForUpdate(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_READ)
	@Query("select studyGroup from StudyGroup studyGroup where studyGroup.id = :id")
	Optional<StudyGroup> findByIdForRead(@Param("id") Long id);
}
