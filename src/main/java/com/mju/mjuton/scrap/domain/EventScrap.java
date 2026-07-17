package com.mju.mjuton.scrap.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.event.domain.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "event_scraps",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}),
		indexes = @Index(name = "idx_event_scraps_user_created",
				columnList = "user_id, created_at, event_scrap_id"))
public class EventScrap {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_scrap_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Event event;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected EventScrap() {}

	public EventScrap(User user, Event event) {
		this.user = user;
		this.event = event;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getEventId() { return event.getId(); }
	public Event getEvent() { return event; }
	public Instant getCreatedAt() { return createdAt; }
}
