package com.mju.mjuton.meetup.domain;

import com.mju.mjuton.auth.domain.User;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "meetup_votes", uniqueConstraints = {
		@UniqueConstraint(name = "uk_meetup_votes_meetup_user", columnNames = {"meetup_id", "user_id"})
})
public class MeetupVote {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "vote_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "meetup_id", nullable = false)
	private Meetup meetup;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "option_id", nullable = false)
	private MeetupOption option;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	private Instant updatedAt;

	protected MeetupVote() {}

	public MeetupVote(Meetup meetup, MeetupOption option, User user) {
		this.meetup = meetup;
		this.option = option;
		this.user = user;
		this.updatedAt = Instant.now();
	}

	public void changeOption(MeetupOption option) {
		this.option = option;
		this.updatedAt = Instant.now();
	}

	public Long getMeetupId() { return meetup.getId(); }
	public Long getOptionId() { return option.getId(); }
	public Long getUserId() { return user.getId(); }
}
