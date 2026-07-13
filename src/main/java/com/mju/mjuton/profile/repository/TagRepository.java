package com.mju.mjuton.profile.repository;

import com.mju.mjuton.profile.domain.Tag;
import com.mju.mjuton.profile.domain.TagType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {
	Optional<Tag> findByTypeAndName(TagType type, String name);
}
