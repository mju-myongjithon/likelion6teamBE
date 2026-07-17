package com.mju.mjuton.meetup.repository;

import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.domain.MeetupStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {
	List<Meetup> findByGroup_IdAndStatusNotOrderByCreatedAtAscIdAsc(long groupId, MeetupStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Meetup m where m.id = :id")
	Optional<Meetup> findByIdForUpdate(@Param("id") long id);
}
