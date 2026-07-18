package com.mju.mjuton.event.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.domain.EventCategory;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.global.ApiException;
import com.mju.mjuton.group.repository.StudyGroupRepository;
import com.mju.mjuton.profile.domain.Tag;
import com.mju.mjuton.profile.domain.TagType;
import com.mju.mjuton.profile.repository.TagRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
	private final EventRepository events;
	private final TagRepository tags;
	private final UserRepository users;
	private final StudyGroupRepository groups;
	private final EventWriteLock eventWriteLock;

	public EventService(EventRepository events, TagRepository tags, UserRepository users,
			StudyGroupRepository groups, EventWriteLock eventWriteLock) {
		this.events = events;
		this.tags = tags;
		this.users = users;
		this.groups = groups;
		this.eventWriteLock = eventWriteLock;
	}

	public EventDetail create(long userId, EventValues values) {
		return eventWriteLock.execute(() -> createInTransaction(userId, values));
	}

	private EventDetail createInTransaction(long userId, EventValues values) {
		User creator = users.findById(userId).orElseThrow(EventService::authenticationRequired);
		NormalizedValues normalized = normalize(values);
		Event event = new Event(creator, normalized.title(), normalized.description(), normalized.organizer(),
				normalized.applicationDeadlineAt(), normalized.startsAt(), normalized.endsAt(), normalized.location(),
				normalized.relatedUrl(), normalized.posterUrl());
		event.replaceTags(resolveTags(normalized.tags()));
		return EventDetail.from(events.saveAndFlush(event));
	}

	@Transactional(readOnly = true)
	public List<EventSummary> findAll() {
		return events.findAllByOrderByCreatedAtDescIdDesc().stream().map(EventSummary::from).toList();
	}

	@Transactional(readOnly = true)
	public EventDetail find(long eventId) {
		return EventDetail.from(findEvent(eventId));
	}

	public EventDetail update(long userId, long eventId, EventValues values) {
		return eventWriteLock.execute(() -> updateInTransaction(userId, eventId, values));
	}

	private EventDetail updateInTransaction(long userId, long eventId, EventValues values) {
		ensureUserExists(userId);
		Event event = findEvent(eventId);
		ensureCreator(event, userId);
		NormalizedValues normalized = normalize(values);
		event.update(normalized.title(), normalized.description(), normalized.organizer(),
				normalized.applicationDeadlineAt(), normalized.startsAt(), normalized.endsAt(), normalized.location(),
				normalized.relatedUrl(), normalized.posterUrl(), resolveTags(normalized.tags()));
		return EventDetail.from(events.saveAndFlush(event));
	}

	@Transactional
	public void delete(long userId, long eventId) {
		ensureUserExists(userId);
		Event event = findEvent(eventId);
		ensureCreator(event, userId);
		groups.unlinkEvent(eventId);
		events.delete(event);
	}

	private Event findEvent(long eventId) {
		return events.findWithEventTagsById(eventId).orElseThrow(EventService::eventNotFound);
	}

	private void ensureUserExists(long userId) {
		if (!users.existsById(userId)) throw authenticationRequired();
	}

	private void ensureCreator(Event event, long userId) {
		if (event.getCreatorUserId() != userId) {
			throw new ApiException(HttpStatus.FORBIDDEN, "EVENT_FORBIDDEN", "행사 등록자만 변경할 수 있습니다.");
		}
	}

	private NormalizedValues normalize(EventValues values) {
		if (values == null) throw invalidRequest("요청 본문은 필수입니다.");
		String title = required(values.title(), "행사명", 100);
		String description = required(values.description(), "행사 소개", 2000);
		String organizer = required(values.organizer(), "주최자", 100);
		Instant deadline = required(values.applicationDeadlineAt(), "신청 마감일");
		Instant startsAt = required(values.startsAt(), "시작 일시");
		Instant endsAt = required(values.endsAt(), "종료 일시");
		if (deadline.isAfter(startsAt) || !startsAt.isBefore(endsAt)) {
			throw invalidRequest("신청 마감일은 시작 일시 이하여야 하고, 시작 일시는 종료 일시보다 빨라야 합니다.");
		}
		String location = required(values.location(), "행사 장소", 200);
		String relatedUrl = url(values.relatedUrl());
		String posterUrl = optionalUrl(values.posterUrl(), "포스터 이미지 URL");
		return new NormalizedValues(title, description, organizer, deadline, startsAt, endsAt, location,
				relatedUrl, posterUrl, tagNames(values.tags()));
	}

	private String required(String value, String field, int maxLength) {
		if (value == null) throw invalidRequest(field + "은(는) 필수입니다.");
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw invalidRequest(field + "은(는) 1~" + maxLength + "자여야 합니다.");
		}
		return normalized;
	}

	private Instant required(Instant value, String field) {
		if (value == null) throw invalidRequest(field + "은(는) 필수입니다.");
		return value;
	}

	private String url(String value) {
		String normalized = required(value, "관련 링크", 2048);
		return absoluteHttpUrl(normalized, "관련 링크");
	}

	private String optionalUrl(String value, String field) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.trim();
		if (normalized.length() > 2048) throw invalidRequest(field + "은(는) 최대 2048자여야 합니다.");
		if (normalized.startsWith("/")) return sameOriginPath(normalized, field);
		return absoluteHttpUrl(normalized, field);
	}

	private String sameOriginPath(String value, String field) {
		try {
			URI uri = URI.create(value);
			if (value.startsWith("//") || uri.getRawAuthority() != null || uri.getRawPath() == null) {
				throw invalidRequest(field + "은(는) 단일 슬래시로 시작하는 same-origin 경로여야 합니다.");
			}
			String decodedPath = URLDecoder.decode(uri.getRawPath(), StandardCharsets.UTF_8)
					.replace('\\', '/');
			for (String segment : decodedPath.split("/")) {
				if ("..".equals(segment)) {
					throw invalidRequest(field + "에 상위 경로 이동을 포함할 수 없습니다.");
				}
			}
			return value;
		} catch (IllegalArgumentException exception) {
			throw invalidRequest(field + "은(는) 유효한 same-origin 경로여야 합니다.");
		}
	}

	private String absoluteHttpUrl(String normalized, String field) {
		try {
			URI uri = URI.create(normalized);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					|| uri.getHost() == null) throw invalidRequest(field + "은(는) 절대형 HTTP 또는 HTTPS URL이어야 합니다.");
			return normalized;
		} catch (IllegalArgumentException exception) {
			throw invalidRequest(field + "은(는) 절대형 HTTP 또는 HTTPS URL이어야 합니다.");
		}
	}

	private List<String> tagNames(List<String> values) {
		if (values == null) throw invalidRequest("행사 태그 배열은 필수입니다.");
		if (values.size() > 20) throw invalidRequest("행사 태그는 최대 20개까지 입력할 수 있습니다.");
		List<String> normalized = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		for (String value : values) {
			String name = required(value, "행사 태그", 50);
			if (!unique.add(name)) throw invalidRequest("중복된 행사 태그를 입력할 수 없습니다.");
			normalized.add(name);
		}
		return normalized;
	}

	private List<Tag> resolveTags(List<String> names) {
		return names.stream().map(name -> tags.findByTypeAndName(TagType.EVENT, name)
				.orElseGet(() -> tags.save(new Tag(TagType.EVENT, name)))).toList();
	}

	private static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException eventNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "행사가 존재하지 않습니다.");
	}

	public record EventValues(String title, String description, String organizer, Instant applicationDeadlineAt,
			Instant startsAt, Instant endsAt, String location, String relatedUrl, String posterUrl, List<String> tags) {
		public EventValues(String title, String description, String organizer, Instant applicationDeadlineAt,
				Instant startsAt, Instant endsAt, String location, String relatedUrl, List<String> tags) {
			this(title, description, organizer, applicationDeadlineAt, startsAt, endsAt, location, relatedUrl, null,
					tags);
		}
	}

	private record NormalizedValues(String title, String description, String organizer,
			Instant applicationDeadlineAt, Instant startsAt, Instant endsAt, String location, String relatedUrl,
			String posterUrl, List<String> tags) {}

	public record EventSummary(Long eventId, String title, EventCategory category, Instant applicationDeadlineAt,
			Instant startsAt, String location, String posterUrl, Instant createdAt) {
		static EventSummary from(Event event) {
			return new EventSummary(event.getId(), event.getTitle(), event.getCategory(),
					event.getApplicationDeadlineAt(), event.getStartsAt(), event.getLocation(), event.getPosterUrl(),
					event.getCreatedAt());
		}
	}

	public record EventDetail(Long eventId, Long creatorUserId, String title, EventCategory category,
			String description, String organizer, Instant applicationDeadlineAt, Instant startsAt, Instant endsAt,
			String location, String relatedUrl, String posterUrl, List<String> tags, Instant createdAt,
			Instant updatedAt) {
		static EventDetail from(Event event) {
			return new EventDetail(event.getId(), event.getCreatorUserId(), event.getTitle(), event.getCategory(),
					event.getDescription(), event.getOrganizer(), event.getApplicationDeadlineAt(), event.getStartsAt(),
					event.getEndsAt(), event.getLocation(), event.getRelatedUrl(), event.getPosterUrl(),
					event.getEventTags().stream().map(tag -> tag.getName()).toList(), event.getCreatedAt(),
					event.getUpdatedAt());
		}
	}
}
