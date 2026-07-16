package com.mju.mjuton.event.domain;

import com.mju.mjuton.profile.domain.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "event_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "tag_id"}))
public class EventTag {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "event_tag_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;
	@Column(nullable = false)
	private int position;

	protected EventTag() {}

	EventTag(Event event, Tag tag, int position) {
		this.event = event;
		this.tag = tag;
		this.position = position;
	}

	boolean references(Tag candidate) {
		return tag == candidate || tag.getId().equals(candidate.getId());
	}

	void moveTo(int position) {
		this.position = position;
	}

	public String getName() { return tag.getName(); }
}
