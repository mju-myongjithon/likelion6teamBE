package com.mju.mjuton.eventapplication.repository;

import com.mju.mjuton.eventapplication.domain.EventApplication;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventApplicationRepository extends JpaRepository<EventApplication, Long> {
	boolean existsByUser_IdAndEvent_Id(Long userId, Long eventId);
	long deleteByUser_IdAndEvent_Id(Long userId, Long eventId);

	@EntityGraph(attributePaths = "event")
	List<EventApplication> findByUser_IdOrderByEvent_StartsAtAscIdAsc(Long userId);
}
