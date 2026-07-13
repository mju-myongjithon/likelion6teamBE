package com.mju.mjuton.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "tags", uniqueConstraints = @UniqueConstraint(columnNames = {"type", "name"}))
public class Tag {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "tag_id")
	private Long id;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TagType type;
	@Column(nullable = false, length = 50)
	private String name;

	protected Tag() {}

	public Tag(TagType type, String name) {
		this.type = type;
		this.name = name;
	}

	public Long getId() { return id; }
	public TagType getType() { return type; }
	public String getName() { return name; }
}
