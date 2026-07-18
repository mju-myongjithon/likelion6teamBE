package com.mju.mjuton.eventapplication.domain;

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
@Table(name = "event_applications",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}),
		indexes = @Index(name = "idx_event_applications_user_applied",
				columnList = "user_id, applied_at, event_application_id"))
public class EventApplication {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_application_id")
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
	private Instant appliedAt;

	protected EventApplication() {}

	public EventApplication(User user, Event event) {
		this.user = user;
		this.event = event;
		this.appliedAt = Instant.now();
	}

	public Long getId() { return id; }
	public Event getEvent() { return event; }
	public Instant getAppliedAt() { return appliedAt; }
}
