package com.mju.mjuton.event.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.profile.domain.Tag;
import com.mju.mjuton.scrap.domain.EventScrap;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "creator_user_id", nullable = false)
	private User creator;
	@Column(nullable = false, length = 100)
	private String title;
	@Column(nullable = false, length = 2000)
	private String description;
	@Column(nullable = false, length = 100)
	private String organizer;
	@Column(nullable = false)
	private Instant applicationDeadlineAt;
	@Column(nullable = false)
	private Instant startsAt;
	@Column(nullable = false)
	private Instant endsAt;
	@Column(nullable = false, length = 200)
	private String location;
	@Column(nullable = false, length = 2048)
	private String relatedUrl;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant updatedAt;
	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<EventTag> eventTags = new ArrayList<>();
	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<EventScrap> scraps = new ArrayList<>();

	protected Event() {}

	public Event(User creator, String title, String description, String organizer,
			Instant applicationDeadlineAt, Instant startsAt, Instant endsAt, String location, String relatedUrl) {
		this.creator = creator;
		updateFields(title, description, organizer, applicationDeadlineAt, startsAt, endsAt, location, relatedUrl);
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void update(String title, String description, String organizer, Instant applicationDeadlineAt,
			Instant startsAt, Instant endsAt, String location, String relatedUrl, List<Tag> tags) {
		updateFields(title, description, organizer, applicationDeadlineAt, startsAt, endsAt, location, relatedUrl);
		replaceTags(tags);
		this.updatedAt = Instant.now();
	}

	public void replaceTags(List<Tag> tags) {
		List<EventTag> existingTags = new ArrayList<>(eventTags);
		eventTags.clear();
		for (int index = 0; index < tags.size(); index++) {
			Tag tag = tags.get(index);
			EventTag eventTag = null;
			for (EventTag candidate : existingTags) {
				if (candidate.references(tag)) {
					eventTag = candidate;
					break;
				}
			}
			if (eventTag == null) eventTag = new EventTag(this, tag, index);
			existingTags.remove(eventTag);
			eventTag.moveTo(index);
			eventTags.add(eventTag);
		}
	}

	private void updateFields(String title, String description, String organizer, Instant applicationDeadlineAt,
			Instant startsAt, Instant endsAt, String location, String relatedUrl) {
		this.title = title;
		this.description = description;
		this.organizer = organizer;
		this.applicationDeadlineAt = applicationDeadlineAt;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.location = location;
		this.relatedUrl = relatedUrl;
	}

	public Long getId() { return id; }
	public Long getCreatorUserId() { return creator.getId(); }
	public String getTitle() { return title; }
	public EventCategory getCategory() { return EventCategory.HACKATHON; }
	public String getDescription() { return description; }
	public String getOrganizer() { return organizer; }
	public Instant getApplicationDeadlineAt() { return applicationDeadlineAt; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public String getLocation() { return location; }
	public String getRelatedUrl() { return relatedUrl; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public List<EventTag> getEventTags() { return List.copyOf(eventTags); }
}
