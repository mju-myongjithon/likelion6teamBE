package com.mju.mjuton.meetup.repository;

import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.domain.MeetupStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MeetupRepository extends JpaRepository<Meetup, Long> {
	List<Meetup> findByGroup_IdAndStatusNotOrderByCreatedAtAscIdAsc(long groupId, MeetupStatus status);

	@Query("""
			select distinct meetup
			from Meetup meetup
			left join meetup.group.members member
			where (meetup.group.leader.id = :userId or member.user.id = :userId)
					and meetup.status <> :excludedStatus
					and meetup.meetingDate between :startDate and :endDate
			order by meetup.meetingDate asc, meetup.meetingTime asc, meetup.id asc
			""")
	List<Meetup> findMineBetween(
			@Param("userId") long userId,
			@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate,
			@Param("excludedStatus") MeetupStatus excludedStatus);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from Meetup m where m.id = :id")
	Optional<Meetup> findByIdForUpdate(@Param("id") long id);
}
