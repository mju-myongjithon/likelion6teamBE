package com.mju.mjuton.listing.service;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ListingItem", description = "category로 구분하는 스터디·해커톤 목록 항목",
		discriminatorProperty = "category",
		discriminatorMapping = {
				@DiscriminatorMapping(value = "STUDY", schema = StudyListingItem.class),
				@DiscriminatorMapping(value = "HACKATHON", schema = HackathonListingItem.class)
		},
		oneOf = {StudyListingItem.class, HackathonListingItem.class})
public sealed interface ListingItem permits StudyListingItem, HackathonListingItem {
	String category();
	Instant createdAt();
}
