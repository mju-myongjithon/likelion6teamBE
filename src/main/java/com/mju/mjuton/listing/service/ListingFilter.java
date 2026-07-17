package com.mju.mjuton.listing.service;

import com.mju.mjuton.global.ApiException;
import org.springframework.http.HttpStatus;

public enum ListingFilter {
	ALL,
	STUDY,
	HACKATHON;

	public static ListingFilter from(String value) {
		if (value == null) return ALL;
		try {
			return ListingFilter.valueOf(value);
		} catch (IllegalArgumentException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "filter는 ALL, STUDY, HACKATHON 중 하나여야 합니다.");
		}
	}
}
