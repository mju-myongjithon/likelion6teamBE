package com.mju.mjuton.recommendation.service;

import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.GroupJoinApplicationStatus;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupJoinApplicationRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.GroupMemberRepository.GroupMemberCount;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.listing.service.ListingFilter;
import com.mju.mjuton.profile.domain.Profile;
import com.mju.mjuton.profile.domain.ProfileTag;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.repository.ProfileRepository;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.AiAssessment;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateInput;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.CandidateKey;
import com.mju.mjuton.recommendation.service.RecommendationAiClient.ProfileInput;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {
	private static final int MAX_AI_CANDIDATES = 30;
	private static final Comparator<ScoredCandidate> RULE_ORDER = Comparator
			.comparingInt(ScoredCandidate::ruleScore).reversed()
			.thenComparing(Comparator.comparing(ScoredCandidate::createdAt).reversed())
			.thenComparing(ScoredCandidate::category)
			.thenComparingLong(ScoredCandidate::targetId);
	private static final Comparator<RecommendationItem> RESULT_ORDER = Comparator
			.comparingInt(RecommendationItem::score).reversed()
			.thenComparing(Comparator.comparingInt(RecommendationItem::ruleScore).reversed())
			.thenComparing(RecommendationItem::category)
			.thenComparingLong(RecommendationItem::targetId);

	private final ProfileRepository profiles;
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final GroupJoinApplicationRepository applications;
	private final EventRepository events;
	private final RecommendationAiClient aiClient;

	public RecommendationService(ProfileRepository profiles, StudyGroupRepository groups,
			GroupMemberRepository members, GroupJoinApplicationRepository applications,
			EventRepository events, RecommendationAiClient aiClient) {
		this.profiles = profiles;
		this.groups = groups;
		this.members = members;
		this.applications = applications;
		this.events = events;
		this.aiClient = aiClient;
	}

	public List<RecommendationItem> recommend(long userId, ListingFilter filter, int limit) {
		Profile profile = profiles.findById(userId).orElseThrow(() ->
				new ApiException(HttpStatus.NOT_FOUND, "PROFILE_NOT_FOUND", "프로필이 존재하지 않습니다."));
		ProfileSignals signals = ProfileSignals.from(profile);
		List<ScoredCandidate> candidates = new ArrayList<>();
		if (filter != ListingFilter.HACKATHON) candidates.addAll(studyCandidates(userId, signals));
		if (filter != ListingFilter.STUDY) candidates.addAll(eventCandidates(userId, signals));
		candidates.sort(RULE_ORDER);
		if (candidates.size() > MAX_AI_CANDIDATES) {
			candidates = new ArrayList<>(candidates.subList(0, MAX_AI_CANDIDATES));
		}
		Map<CandidateKey, AiAssessment> assessments = aiClient.assess(signals.toAiInput(),
				candidates.stream().map(ScoredCandidate::toAiInput).toList());
		return candidates.stream()
				.map(candidate -> candidate.toItem(assessments.get(candidate.key())))
				.sorted(RESULT_ORDER)
				.limit(limit)
				.toList();
	}

	private List<ScoredCandidate> studyCandidates(long userId, ProfileSignals signals) {
		List<StudyGroup> found = groups.findAllByOrderByCreatedAtDescIdDesc();
		if (found.isEmpty()) return List.of();
		List<Long> ids = found.stream().map(StudyGroup::getId).toList();
		Map<Long, GroupMemberCount> counts = members.countMembersByGroupIds(ids).stream()
				.collect(Collectors.toMap(GroupMemberCount::getGroupId, Function.identity()));
		Set<Long> joinedIds = new HashSet<>(members.findGroupIdsByUserIdAndGroupIds(userId, ids));
		Set<Long> pendingIds = new HashSet<>(applications.findGroupIdsByApplicantIdAndStatusAndGroupIds(
				userId, GroupJoinApplicationStatus.PENDING, ids));
		List<ScoredCandidate> result = new ArrayList<>();
		for (StudyGroup group : found) {
			long memberCount = currentMemberCount(group, counts);
			if (!group.isRecruiting() || group.getLeaderUserId() == userId
					|| memberCount >= group.getMaxMemberCount()
					|| joinedIds.contains(group.getId()) || pendingIds.contains(group.getId())) continue;
			List<String> attributes = group.getRecruitingRoles().stream()
					.map(role -> role.getSkill() == null ? role.getRole() : role.getRole() + " / " + role.getSkill())
					.toList();
			result.add(score(signals, "STUDY", group.getId(), group.getTitle(), group.getDescription(),
					group.getLocation(), attributes, group.getCreatedAt(),
					"스터디 모임 " + group.getMeetingRule(), group.getMaxMemberCount() - memberCount));
		}
		return result;
	}

	private List<ScoredCandidate> eventCandidates(long userId, ProfileSignals signals) {
		Instant now = Instant.now();
		List<ScoredCandidate> result = new ArrayList<>();
		for (Event event : events.findAllByOrderByCreatedAtDescIdDesc()) {
			if (event.getCreatorUserId() == userId || event.getApplicationDeadlineAt().isBefore(now)) continue;
			List<String> attributes = event.getEventTags().stream().map(tag -> tag.getName()).toList();
			long daysUntilDeadline = Math.max(0, Duration.between(now, event.getApplicationDeadlineAt()).toDays());
			result.add(score(signals, "HACKATHON", event.getId(), event.getTitle(), event.getDescription(),
					event.getLocation(), attributes, event.getCreatedAt(),
					"해커톤 행사 " + event.getOrganizer(), daysUntilDeadline));
		}
		return result;
	}

	private ScoredCandidate score(ProfileSignals signals, String category, long targetId, String title,
			String description, String location, List<String> attributes, Instant createdAt,
			String categoryText, long availabilityValue) {
		String searchable = normalize(String.join(" ", title, description, categoryText,
				String.join(" ", attributes)));
		List<String> matchedInterests = matches(signals.interests(), searchable);
		List<String> matchedPurposes = matches(signals.purposes(), searchable);
		List<String> matchedRoles = matches(signals.roles(), searchable);
		int interestScore = ratioScore(matchedInterests.size(), signals.interests().size(), 40);
		int purposeScore = ratioScore(matchedPurposes.size(), signals.purposes().size(), 15);
		int roleScore = ratioScore(matchedRoles.size(), signals.roles().size(), 25);
		boolean sameLocation = sameLocation(signals.residenceArea(), location);
		int locationScore = sameLocation ? 10 : 0;
		int availabilityScore = category.equals("STUDY")
				? (availabilityValue >= 3 ? 10 : availabilityValue == 2 ? 8 : 5)
				: (availabilityValue >= 7 ? 10 : availabilityValue >= 3 ? 7 : 4);
		int ruleScore = interestScore + purposeScore + roleScore + locationScore + availabilityScore;
		List<String> reasons = fallbackReasons(category, matchedInterests, matchedPurposes, matchedRoles,
				sameLocation, location, availabilityValue);
		return new ScoredCandidate(category, targetId, title, description, location, attributes,
				createdAt, ruleScore, reasons);
	}

	private List<String> fallbackReasons(String category, List<String> interests, List<String> purposes,
			List<String> roles, boolean sameLocation, String location, long availabilityValue) {
		List<String> reasons = new ArrayList<>();
		if (!interests.isEmpty()) reasons.add("관심사 " + quoted(interests) + "와 관련이 있어요.");
		if (!roles.isEmpty()) reasons.add("희망 역할 " + quoted(roles) + "과 잘 맞아요.");
		if (!purposes.isEmpty()) reasons.add("활동 목적 " + quoted(purposes) + "에 적합해요.");
		if (sameLocation && reasons.size() < 3) reasons.add("선호 지역과 같은 " + location + "에서 진행돼요.");
		if (reasons.size() < 3) {
			reasons.add(category.equals("STUDY")
					? "현재 모집 중이며 " + availabilityValue + "자리가 남아 있어요."
					: "현재 신청할 수 있는 해커톤·행사예요.");
		}
		return List.copyOf(reasons.subList(0, Math.min(3, reasons.size())));
	}

	private List<String> matches(List<String> values, String searchable) {
		return values.stream().filter(value -> searchable.contains(normalize(value))).toList();
	}

	private int ratioScore(int matched, int total, int maxScore) {
		if (total == 0) return 0;
		return (int) Math.round((double) matched / total * maxScore);
	}

	private boolean sameLocation(String residenceArea, String location) {
		String residence = normalize(residenceArea);
		String candidate = normalize(location);
		return candidate.contains("온라인") || candidate.contains(residence) || residence.contains(candidate);
	}

	private String normalize(String value) {
		return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
				.toLowerCase().replaceAll("\\s+", "");
	}

	private String quoted(List<String> values) {
		return values.stream().limit(3).map(value -> "'" + value + "'").collect(Collectors.joining(", "));
	}

	private long currentMemberCount(StudyGroup group, Map<Long, GroupMemberCount> counts) {
		GroupMemberCount count = counts.get(group.getId());
		if (count == null) return 1;
		return count.getStoredMemberCount() + (count.getLeaderRowCount() > 0 ? 0 : 1);
	}

	public enum RecommendationMode {
		HYBRID,
		RULE_FALLBACK
	}

	public record RecommendationItem(String category, Long targetId, String title, String location,
			int score, int ruleScore, Integer aiScore, RecommendationMode mode, List<String> reasons) {}

	private record ProfileSignals(String schoolName, String departmentName, String residenceArea, String bio,
			List<String> interests, List<String> purposes, List<String> roles) {
		static ProfileSignals from(Profile profile) {
			return new ProfileSignals(profile.getSchoolName(), profile.getDepartmentName(),
					profile.getResidenceArea(), profile.getBio(), names(profile, TagType.INTEREST),
					names(profile, TagType.PURPOSE), names(profile, TagType.ROLE));
		}

		private static List<String> names(Profile profile, TagType type) {
			return profile.getProfileTags().stream().map(ProfileTag::getTag)
					.filter(tag -> tag.getType() == type).map(tag -> tag.getName()).toList();
		}

		ProfileInput toAiInput() {
			return new ProfileInput(schoolName, departmentName, residenceArea, bio,
					interests, purposes, roles);
		}
	}

	private record ScoredCandidate(String category, Long targetId, String title, String description,
			String location, List<String> attributes, Instant createdAt, int ruleScore,
			List<String> fallbackReasons) {
		CandidateKey key() {
			return new CandidateKey(category, targetId);
		}

		CandidateInput toAiInput() {
			return new CandidateInput(key(), title, description, location, attributes, ruleScore);
		}

		RecommendationItem toItem(AiAssessment assessment) {
			if (assessment == null) {
				return new RecommendationItem(category, targetId, title, location, ruleScore, ruleScore,
						null, RecommendationMode.RULE_FALLBACK, fallbackReasons);
			}
			int score = (int) Math.round(ruleScore * 0.6 + assessment.score() * 0.4);
			return new RecommendationItem(category, targetId, title, location, score, ruleScore,
					assessment.score(), RecommendationMode.HYBRID, assessment.reasons());
		}
	}
}
