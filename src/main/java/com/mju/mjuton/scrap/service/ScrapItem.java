package com.mju.mjuton.scrap.service;

import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ScrapItem", description = "category로 구분하는 저장한 스터디·해커톤 항목",
		discriminatorProperty = "category",
		discriminatorMapping = {
				@DiscriminatorMapping(value = "STUDY", schema = StudyScrapItem.class),
				@DiscriminatorMapping(value = "HACKATHON", schema = EventScrapItem.class)
		},
		oneOf = {StudyScrapItem.class, EventScrapItem.class})
public sealed interface ScrapItem permits StudyScrapItem, EventScrapItem {
	String category();
	Instant scrappedAt();
}
