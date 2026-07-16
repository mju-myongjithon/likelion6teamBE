package com.mju.mjuton.group.domain;

import com.mju.mjuton.auth.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "groups")
public class StudyGroup {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "group_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "leader_user_id", nullable = false)
	private User leader;
	@Column(nullable = false, length = 100)
	private String title;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GroupCategory category;
	@Column(nullable = false, length = 2000)
	private String description;
	@Column(nullable = false)
	private int maxMemberCount;
	@Column(nullable = false, length = 1000)
	private String meetingRule;
	@Column(nullable = false, length = 200)
	private String location;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant updatedAt;
	@OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<RecruitingRole> recruitingRoles = new ArrayList<>();

	protected StudyGroup() {}

	public StudyGroup(User leader, String title, String description, int maxMemberCount,
			String meetingRule, String location) {
		this.leader = leader;
		this.category = GroupCategory.STUDY;
		updateFields(title, description, maxMemberCount, meetingRule, location);
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void update(String title, String description, int maxMemberCount, String meetingRule,
			String location, List<RoleValues> roles) {
		updateFields(title, description, maxMemberCount, meetingRule, location);
		replaceRoles(roles);
		this.updatedAt = Instant.now();
	}

	public void replaceRoles(List<RoleValues> roles) {
		recruitingRoles.clear();
		for (int index = 0; index < roles.size(); index++) {
			RoleValues values = roles.get(index);
			recruitingRoles.add(new RecruitingRole(this, values.role(), values.skill(), index));
		}
	}

	private void updateFields(String title, String description, int maxMemberCount, String meetingRule,
			String location) {
		this.title = title;
		this.description = description;
		this.maxMemberCount = maxMemberCount;
		this.meetingRule = meetingRule;
		this.location = location;
	}

	public Long getId() { return id; }
	public Long getLeaderUserId() { return leader.getId(); }
	public String getTitle() { return title; }
	public GroupCategory getCategory() { return category; }
	public String getDescription() { return description; }
	public int getMaxMemberCount() { return maxMemberCount; }
	public String getMeetingRule() { return meetingRule; }
	public String getLocation() { return location; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public List<RecruitingRole> getRecruitingRoles() { return List.copyOf(recruitingRoles); }

	public record RoleValues(String role, String skill) {}
}
