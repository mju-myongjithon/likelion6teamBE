package com.mju.mjuton.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_recruiting_roles")
public class RecruitingRole {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "recruiting_role_id")
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	private StudyGroup group;
	@Column(nullable = false, length = 50)
	private String role;
	@Column(length = 100)
	private String skill;
	@Column(nullable = false)
	private int position;

	protected RecruitingRole() {}

	RecruitingRole(StudyGroup group, String role, String skill, int position) {
		this.group = group;
		this.role = role;
		this.skill = skill;
		this.position = position;
	}

	public String getRole() { return role; }
	public String getSkill() { return skill; }
}
