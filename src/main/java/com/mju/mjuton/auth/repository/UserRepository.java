package com.mju.mjuton.auth.repository;

import com.mju.mjuton.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);
	boolean existsByEmail(String email);
}
