package com.mju.mjuton.scrap.repository;

import com.mju.mjuton.scrap.domain.GroupScrap;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupScrapRepository extends JpaRepository<GroupScrap, Long> {
	boolean existsByUser_IdAndGroup_Id(Long userId, Long groupId);
	long deleteByUser_IdAndGroup_Id(Long userId, Long groupId);

	@EntityGraph(attributePaths = {"group", "group.leader"})
	List<GroupScrap> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);
}
