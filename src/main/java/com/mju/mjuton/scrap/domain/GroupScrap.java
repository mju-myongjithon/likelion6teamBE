package com.mju.mjuton.scrap.domain;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.group.domain.StudyGroup;
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
@Table(name = "group_scraps",
		uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "group_id"}),
		indexes = @Index(name = "idx_group_scraps_user_created",
				columnList = "user_id, created_at, group_scrap_id"))
public class GroupScrap {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_scrap_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private StudyGroup group;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected GroupScrap() {}

	public GroupScrap(User user, StudyGroup group) {
		this.user = user;
		this.group = group;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public StudyGroup getGroup() { return group; }
	public Instant getCreatedAt() { return createdAt; }
}
