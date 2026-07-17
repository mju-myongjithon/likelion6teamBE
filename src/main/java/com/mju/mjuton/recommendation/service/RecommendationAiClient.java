package com.mju.mjuton.recommendation.service;

import java.util.List;
import java.util.Map;

public interface RecommendationAiClient {
	Map<CandidateKey, AiAssessment> assess(ProfileInput profile, List<CandidateInput> candidates);

	record CandidateKey(String category, Long targetId) {}

	record ProfileInput(String schoolName, String departmentName, String residenceArea, String bio,
			List<String> interests, List<String> purposes, List<String> roles) {}

	record CandidateInput(CandidateKey key, String title, String description, String location,
			List<String> attributes, int ruleScore) {}

	record AiAssessment(int score, List<String> reasons) {}
}
