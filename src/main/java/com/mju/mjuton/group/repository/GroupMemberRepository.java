package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.GroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
	boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
	Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);
	List<GroupMember> findByGroup_IdOrderByJoinedAtAscIdAsc(Long groupId);
	long countByGroup_Id(Long groupId);
}
