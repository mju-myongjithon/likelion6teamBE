package com.mju.mjuton.cafe.service;

import java.util.List;

public interface CafeSearchClient {
	List<CafeCandidate> searchNearby(double latitude, double longitude);

	record CafeCandidate(String name, Double latitude, Double longitude, String address, String phone,
			Boolean parkingAvailable, String parkingInfo) {}
}
