package com.mju.mjuton.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_verifications")
public class EmailVerification {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "verification_id")
	private Long id;
	@Column(nullable = false)
	private String email;
	@Column(nullable = false, length = 60)
	private String codeHash;
	@Column(nullable = false)
	private Instant createdAt;
	@Column(nullable = false)
	private Instant expiresAt;
	private Instant consumedAt;

	protected EmailVerification() {}

	public EmailVerification(String email, String codeHash, Instant now) {
		this.email = email;
		this.codeHash = codeHash;
		this.createdAt = now;
		this.expiresAt = now.plusSeconds(300);
	}

	public String getCodeHash() { return codeHash; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getExpiresAt() { return expiresAt; }
	public Instant getConsumedAt() { return consumedAt; }
	public void consume(Instant now) { this.consumedAt = now; }
}
