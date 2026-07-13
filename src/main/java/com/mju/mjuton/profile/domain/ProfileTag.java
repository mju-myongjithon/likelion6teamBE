package com.mju.mjuton.profile.domain;

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
@Table(name = "profile_tags", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "tag_id"}))
public class ProfileTag {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private Profile profile;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	protected ProfileTag() {}

	ProfileTag(Profile profile, Tag tag) {
		this.profile = profile;
		this.tag = tag;
	}

	public Tag getTag() { return tag; }
}
