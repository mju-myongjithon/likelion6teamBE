package com.mju.mjuton.cafe.service;

import java.util.Optional;

public interface ResidenceCoordinateResolver {
	Optional<Coordinate> resolve(String residenceArea);

	record Coordinate(double latitude, double longitude) {}
}
