package com.mju.mjuton.auth.repository;

import com.mju.mjuton.auth.domain.EmailVerification;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {
	Optional<EmailVerification> findFirstByEmailOrderByCreatedAtDesc(String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<EmailVerification> findFirstWithLockByEmailOrderByCreatedAtDesc(String email);
}
