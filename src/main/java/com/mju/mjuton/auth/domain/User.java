package com.mju.mjuton.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_id")
	private Long id;
	@Column(nullable = false, unique = true, length = 255)
	private String email;
	@Column(nullable = false, length = 60)
	private String passwordHash;
	@Column(nullable = false, updatable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant updatedAt;

	protected User() {}

	public User(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	public Long getId() { return id; }
	public String getEmail() { return email; }
	public String getPasswordHash() { return passwordHash; }
}
