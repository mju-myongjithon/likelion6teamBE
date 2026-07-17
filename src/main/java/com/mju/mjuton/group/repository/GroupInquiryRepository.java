package com.mju.mjuton.group.repository;

import com.mju.mjuton.group.domain.GroupInquiry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupInquiryRepository extends JpaRepository<GroupInquiry, Long> {
	Page<GroupInquiry> findByGroup_Id(Long groupId, Pageable pageable);
	Optional<GroupInquiry> findByIdAndGroup_Id(Long inquiryId, Long groupId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select inquiry
			from GroupInquiry inquiry
			where inquiry.id = :inquiryId and inquiry.group.id = :groupId
			""")
	Optional<GroupInquiry> findByIdAndGroupIdForUpdate(
			@Param("inquiryId") Long inquiryId, @Param("groupId") Long groupId);
}
