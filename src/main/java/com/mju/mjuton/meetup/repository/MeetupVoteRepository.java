package com.mju.mjuton.meetup.repository;

import com.mju.mjuton.meetup.domain.MeetupVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetupVoteRepository extends JpaRepository<MeetupVote, Long> {
	List<MeetupVote> findByMeetup_Id(long meetupId);
	Optional<MeetupVote> findByMeetup_IdAndUser_Id(long meetupId, long userId);
	void deleteByMeetup_IdAndUser_Id(long meetupId, long userId);
}
