package com.mju.mjuton.profile.domain;

import com.mju.mjuton.auth.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profiles")
public class Profile {
	@Id
	@Column(name = "user_id")
	private Long userId;
	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private User user;
	@Column(nullable = false, length = 50)
	private String name;
	@Column(nullable = false, length = 100)
	private String schoolName;
	@Column(nullable = false, length = 100)
	private String departmentName;
	@Column(nullable = false, length = 100)
	private String residenceArea;
	@Column(length = 500)
	private String bio;
	@Column(length = 2048)
	private String avatarUrl;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant updatedAt;
	@OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<ProfileTag> profileTags = new ArrayList<>();

	protected Profile() {}

	public Profile(User user, String name, String schoolName, String departmentName,
			String residenceArea, String bio, String avatarUrl) {
		this.user = user;
		this.name = name;
		this.schoolName = schoolName;
		this.departmentName = departmentName;
		this.residenceArea = residenceArea;
		this.bio = bio;
		this.avatarUrl = avatarUrl;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public void update(String name, String schoolName, String departmentName,
			String residenceArea, String bio, String avatarUrl) {
		this.name = name;
		this.schoolName = schoolName;
		this.departmentName = departmentName;
		this.residenceArea = residenceArea;
		this.bio = bio;
		this.avatarUrl = avatarUrl;
		this.updatedAt = Instant.now();
	}

	public void replaceTags(List<Tag> tags) {
		profileTags.removeIf(profileTag -> tags.stream().noneMatch(tag -> tag.getId().equals(profileTag.getTag().getId())));
		tags.stream().filter(tag -> profileTags.stream()
				.noneMatch(profileTag -> tag.getId().equals(profileTag.getTag().getId())))
				.forEach(tag -> profileTags.add(new ProfileTag(this, tag)));
	}

	public Long getUserId() { return userId; }
	public String getName() { return name; }
	public String getSchoolName() { return schoolName; }
	public String getDepartmentName() { return departmentName; }
	public String getResidenceArea() { return residenceArea; }
	public String getBio() { return bio; }
	public String getAvatarUrl() { return avatarUrl; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public List<ProfileTag> getProfileTags() { return List.copyOf(profileTags); }
}
