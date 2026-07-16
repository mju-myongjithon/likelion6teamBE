package com.mju.mjuton.event.repository;

import com.mju.mjuton.event.domain.Event;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {
	List<Event> findAllByOrderByCreatedAtDescIdDesc();

	@EntityGraph(attributePaths = "eventTags.tag")
	Optional<Event> findWithEventTagsById(Long id);
}
