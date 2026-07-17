package com.mju.mjuton.scrap.repository;

import com.mju.mjuton.scrap.domain.EventScrap;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventScrapRepository extends JpaRepository<EventScrap, Long> {
	boolean existsByUser_IdAndEvent_Id(Long userId, Long eventId);
	long deleteByUser_IdAndEvent_Id(Long userId, Long eventId);

	@EntityGraph(attributePaths = "event")
	List<EventScrap> findByUser_IdOrderByCreatedAtDescIdDesc(Long userId);
}
