package com.mju.mjuton.meetup.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.cafe.service.CafeRecommendationService.CafeRecommendation;
import com.mju.mjuton.cafe.service.GroupCafeRecommendationService;
import com.mju.mjuton.chat.service.ChatService;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.domain.StudyGroup;
import com.mju.mjuton.group.repository.GroupMemberRepository;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.meetup.domain.Meetup;
import com.mju.mjuton.meetup.domain.MeetupOption;
import com.mju.mjuton.meetup.domain.MeetupStatus;
import com.mju.mjuton.meetup.domain.MeetupVote;
import com.mju.mjuton.meetup.repository.MeetupOptionRepository;
import com.mju.mjuton.meetup.repository.MeetupRepository;
import com.mju.mjuton.meetup.repository.MeetupVoteRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetupService {
	public static final String CHAT_MARKER_PREFIX = "[[MEETUP:";
	public static final String CHAT_MARKER_SUFFIX = "]]";

	private final MeetupRepository meetups;
	private final MeetupOptionRepository options;
	private final MeetupVoteRepository votes;
	private final StudyGroupRepository groups;
	private final GroupMemberRepository members;
	private final UserRepository users;
	private final GroupCafeRecommendationService cafeRecommendations;
	private final ChatService chat;

	public MeetupService(MeetupRepository meetups, MeetupOptionRepository options, MeetupVoteRepository votes,
			StudyGroupRepository groups, GroupMemberRepository members, UserRepository users,
			GroupCafeRecommendationService cafeRecommendations, ChatService chat) {
		this.meetups = meetups;
		this.options = options;
		this.votes = votes;
		this.groups = groups;
		this.members = members;
		this.users = users;
		this.cafeRecommendations = cafeRecommendations;
		this.chat = chat;
	}

	@Transactional
	public MeetupResponse create(long groupId, long userId, CreateValues values) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		User creator = users.findById(userId).orElseThrow(this::authenticationRequired);
		CreateValues normalized = normalize(values);
		Meetup meetup = meetups.saveAndFlush(new Meetup(group, creator, normalized.name(),
				normalized.meetingDate(), normalized.meetingTime()));
		if (normalized.placeMode() == PlaceMode.MIDPOINT) {
			for (CafeRecommendation recommendation
					: cafeRecommendations.recommend(groupId, userId).recommendations()) {
				options.save(new MeetupOption(meetup, recommendation.rank(),
						recommendation.location().placeName(), recommendation.location().latitude(),
						recommendation.location().longitude(), recommendation.detail().address(),
						recommendation.detail().phone(), recommendation.reason()));
			}
		} else {
			options.save(new MeetupOption(meetup, 1, normalized.customAddress(), null, null,
					normalized.customAddress(), null, "사용자가 직접 입력한 장소입니다."));
		}
		options.flush();
		chat.send(groupId, userId, marker(meetup.getId()));
		return response(meetup, userId);
	}

	@Transactional(readOnly = true)
	public List<MeetupResponse> list(long groupId, long userId) {
		StudyGroup group = group(groupId);
		requireMember(group, userId);
		return meetups.findByGroup_IdAndStatusNotOrderByCreatedAtAscIdAsc(groupId, MeetupStatus.CANCELLED)
				.stream().map(meetup -> response(meetup, userId)).toList();
	}

	@Transactional
	public MeetupResponse vote(long groupId, long meetupId, long optionId, long userId) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		Meetup meetup = lockedMeetup(groupId, meetupId);
		requireOpen(meetup);
		MeetupOption option = options.findById(optionId)
				.filter(found -> found.getMeetupId() == meetupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEETUP_OPTION_NOT_FOUND",
						"약속 장소 후보를 찾을 수 없습니다."));
		User user = users.findById(userId).orElseThrow(this::authenticationRequired);
		MeetupVote vote = votes.findByMeetup_IdAndUser_Id(meetupId, userId)
				.orElseGet(() -> new MeetupVote(meetup, option, user));
		vote.changeOption(option);
		votes.saveAndFlush(vote);
		return response(meetup, userId);
	}

	@Transactional
	public MeetupResponse cancelVote(long groupId, long meetupId, long userId) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		Meetup meetup = lockedMeetup(groupId, meetupId);
		requireOpen(meetup);
		votes.deleteByMeetup_IdAndUser_Id(meetupId, userId);
		votes.flush();
		return response(meetup, userId);
	}

	@Transactional
	public MeetupResponse confirm(long groupId, long meetupId, long userId) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		Meetup meetup = lockedMeetup(groupId, meetupId);
		requireManage(group, meetup, userId);
		requireOpen(meetup);
		List<MeetupOption> foundOptions = options.findByMeetup_IdOrderByRankOrderAscIdAsc(meetupId);
		Map<Long, Long> counts = voteCounts(votes.findByMeetup_Id(meetupId));
		MeetupOption winner = foundOptions.stream()
				.filter(option -> counts.getOrDefault(option.getId(), 0L) > 0)
				.max(Comparator.comparingLong((MeetupOption option) -> counts.getOrDefault(option.getId(), 0L))
						.thenComparing(Comparator.comparingInt(MeetupOption::getRankOrder).reversed()))
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MEETUP_VOTE_REQUIRED",
						"한 명 이상 투표한 뒤 약속을 확정할 수 있습니다."));
		meetup.confirm(winner.getId());
		meetups.saveAndFlush(meetup);
		return response(meetup, userId);
	}

	@Transactional
	public void cancel(long groupId, long meetupId, long userId) {
		StudyGroup group = lockedGroup(groupId);
		requireMember(group, userId);
		Meetup meetup = lockedMeetup(groupId, meetupId);
		requireManage(group, meetup, userId);
		meetup.cancel();
		meetups.saveAndFlush(meetup);
	}

	private MeetupResponse response(Meetup meetup, long userId) {
		List<MeetupOption> foundOptions = options.findByMeetup_IdOrderByRankOrderAscIdAsc(meetup.getId());
		List<MeetupVote> foundVotes = votes.findByMeetup_Id(meetup.getId());
		Map<Long, Long> counts = voteCounts(foundVotes);
		Long selectedOptionId = foundVotes.stream().filter(vote -> vote.getUserId() == userId)
				.map(MeetupVote::getOptionId).findFirst().orElse(null);
		List<OptionResponse> optionResponses = foundOptions.stream().map(option -> new OptionResponse(
				option.getId(), option.getRankOrder(), option.getPlaceName(), option.getLatitude(),
				option.getLongitude(), option.getAddress(), option.getPhone(), option.getReason(),
				counts.getOrDefault(option.getId(), 0L))).toList();
		return new MeetupResponse(meetup.getId(), meetup.getGroupId(), meetup.getCreatorUserId(),
				meetup.getCreatorUserId() == userId || meetup.getGroupLeaderUserId() == userId,
				meetup.getName(), meetup.getMeetingDate(), meetup.getMeetingTime(), meetup.getStatus(),
				meetup.getConfirmedOptionId(), selectedOptionId, optionResponses,
				meetup.getCreatedAt(), meetup.getUpdatedAt());
	}

	private Map<Long, Long> voteCounts(List<MeetupVote> foundVotes) {
		Map<Long, Long> counts = new HashMap<>();
		for (MeetupVote vote : foundVotes) counts.merge(vote.getOptionId(), 1L, Long::sum);
		return counts;
	}

	private CreateValues normalize(CreateValues values) {
		if (values == null) throw invalidRequest("요청 본문은 필수입니다.");
		String name = values.name() == null ? "" : values.name().trim();
		if (name.isEmpty() || name.length() > 100) throw invalidRequest("약속명은 1~100자여야 합니다.");
		if (values.meetingDate() == null || values.meetingDate().isBefore(LocalDate.now())) {
			throw invalidRequest("약속 날짜는 오늘 이후여야 합니다.");
		}
		if (values.meetingTime() == null) throw invalidRequest("약속 시간은 필수입니다.");
		PlaceMode mode = values.placeMode() == null ? PlaceMode.MIDPOINT : values.placeMode();
		String address = values.customAddress() == null ? null : values.customAddress().trim();
		if (mode == PlaceMode.CUSTOM && (address == null || address.isEmpty() || address.length() > 255)) {
			throw invalidRequest("직접 지정 장소는 1~255자여야 합니다.");
		}
		return new CreateValues(name, values.meetingDate(), values.meetingTime(), mode, address);
	}

	private StudyGroup lockedGroup(long groupId) {
		return groups.findByIdForUpdate(groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
						"모임을 찾을 수 없습니다."));
	}

	private StudyGroup group(long groupId) {
		return groups.findById(groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND",
						"모임을 찾을 수 없습니다."));
	}

	private Meetup lockedMeetup(long groupId, long meetupId) {
		return meetups.findByIdForUpdate(meetupId)
				.filter(meetup -> meetup.getGroupId() == groupId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MEETUP_NOT_FOUND",
						"약속을 찾을 수 없습니다."));
	}

	private void requireMember(StudyGroup group, long userId) {
		if (group.getLeaderUserId() != userId
				&& !members.existsByGroup_IdAndUser_Id(group.getId(), userId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_MEMBER_REQUIRED",
					"모임 참여자만 약속 기능을 사용할 수 있습니다.");
		}
	}

	private void requireManage(StudyGroup group, Meetup meetup, long userId) {
		if (group.getLeaderUserId() != userId && meetup.getCreatorUserId() != userId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "MEETUP_MANAGER_REQUIRED",
					"모임 리더 또는 약속 생성자만 관리할 수 있습니다.");
		}
	}

	private void requireOpen(Meetup meetup) {
		if (meetup.getStatus() != MeetupStatus.OPEN) {
			throw new ApiException(HttpStatus.CONFLICT, "MEETUP_ALREADY_CLOSED",
					"이미 확정되거나 취소된 약속입니다.");
		}
	}

	private String marker(long meetupId) {
		return CHAT_MARKER_PREFIX + meetupId + CHAT_MARKER_SUFFIX;
	}

	private ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	public enum PlaceMode {
		MIDPOINT,
		CUSTOM
	}

	public record CreateValues(String name, LocalDate meetingDate, LocalTime meetingTime,
			PlaceMode placeMode, String customAddress) {}

	public record OptionResponse(Long optionId, int rank, String placeName, Double latitude, Double longitude,
			String address, String phone, String reason, long voteCount) {}

	public record MeetupResponse(Long meetupId, Long groupId, Long creatorUserId, boolean canManage, String name,
			LocalDate meetingDate, LocalTime meetingTime, MeetupStatus status, Long confirmedOptionId,
			Long selectedOptionId, List<OptionResponse> options, Instant createdAt, Instant updatedAt) {}
}
