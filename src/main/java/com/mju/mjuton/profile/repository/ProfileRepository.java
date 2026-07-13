package com.mju.mjuton.profile.repository;

import com.mju.mjuton.profile.domain.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
	@Override
	@EntityGraph(attributePaths = {"profileTags", "profileTags.tag"})
	Optional<Profile> findById(Long userId);
}
