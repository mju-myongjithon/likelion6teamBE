package com.mju.mjuton.group.domain;

import com.mju.mjuton.auth.domain.User;
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
import java.time.Instant;

@Entity
@Table(name = "group_members",
		uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_member_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private StudyGroup group;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;
	@Column(nullable = false, updatable = false)
	private Instant joinedAt;

	protected GroupMember() {}

	public GroupMember(StudyGroup group, User user) {
		this.group = group;
		this.user = user;
		this.joinedAt = Instant.now();
	}

	public Long getId() { return id; }
	public Long getGroupId() { return group.getId(); }
	public Long getUserId() { return user.getId(); }
	public Instant getJoinedAt() { return joinedAt; }
}
