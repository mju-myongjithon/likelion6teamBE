package com.mju.mjuton.meetup.repository;

import com.mju.mjuton.meetup.domain.MeetupOption;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeetupOptionRepository extends JpaRepository<MeetupOption, Long> {
	List<MeetupOption> findByMeetup_IdOrderByRankOrderAscIdAsc(long meetupId);
}
