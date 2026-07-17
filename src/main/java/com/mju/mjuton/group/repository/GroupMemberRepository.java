package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.GroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
	boolean existsByGroup_IdAndUser_Id(Long groupId, Long userId);
	Optional<GroupMember> findByGroup_IdAndUser_Id(Long groupId, Long userId);
	List<GroupMember> findByGroup_IdOrderByJoinedAtAscIdAsc(Long groupId);
	long countByGroup_Id(Long groupId);

	@Query("""
			select member.group.id
			from GroupMember member
			where member.user.id = :userId and member.group.id in :groupIds
			""")
	List<Long> findGroupIdsByUserIdAndGroupIds(
			@Param("userId") Long userId, @Param("groupIds") List<Long> groupIds);

	@Query("""
			select studyGroup.id as groupId,
					count(member.id) as storedMemberCount,
					coalesce(sum(case when member.user.id = studyGroup.leader.id then 1L else 0L end), 0L)
							as leaderRowCount
			from StudyGroup studyGroup
			left join studyGroup.members member
			where studyGroup.id in :groupIds
			group by studyGroup.id
			""")
	List<GroupMemberCount> countMembersByGroupIds(@Param("groupIds") List<Long> groupIds);

	interface GroupMemberCount {
		Long getGroupId();
		Long getStoredMemberCount();
		Long getLeaderRowCount();
	}
}
