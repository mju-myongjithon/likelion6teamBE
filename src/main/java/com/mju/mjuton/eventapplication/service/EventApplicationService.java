package com.mju.mjuton.eventapplication.service;

import com.mju.mjuton.auth.domain.User;
import com.mju.mjuton.auth.repository.UserRepository;
import com.mju.mjuton.event.domain.Event;
import com.mju.mjuton.event.repository.EventRepository;
import com.mju.mjuton.eventapplication.domain.EventApplication;
import com.mju.mjuton.eventapplication.repository.EventApplicationRepository;
import com.mju.mjuton.global.ApiException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventApplicationService {
	private final EventApplicationRepository applications;
	private final EventRepository events;
	private final UserRepository users;

	public EventApplicationService(EventApplicationRepository applications, EventRepository events,
			UserRepository users) {
		this.applications = applications;
		this.events = events;
		this.users = users;
	}

	@Transactional
	public void apply(long userId, long eventId) {
		User user = users.findByIdForUpdate(userId).orElseThrow(EventApplicationService::authenticationRequired);
		Event event = events.findById(eventId).orElseThrow(EventApplicationService::eventNotFound);
		if (event.getApplicationDeadlineAt().isBefore(Instant.now())) {
			throw new ApiException(HttpStatus.CONFLICT, "EVENT_APPLICATION_CLOSED", "신청 마감일이 지난 행사입니다.");
		}
		if (!applications.existsByUser_IdAndEvent_Id(userId, eventId)) {
			applications.saveAndFlush(new EventApplication(user, event));
		}
	}

	@Transactional
	public void cancel(long userId, long eventId) {
		users.findByIdForUpdate(userId).orElseThrow(EventApplicationService::authenticationRequired);
		applications.deleteByUser_IdAndEvent_Id(userId, eventId);
	}

	@Transactional(readOnly = true)
	public ApplicationStatus status(long userId, long eventId) {
		if (!users.existsById(userId)) throw authenticationRequired();
		if (!events.existsById(eventId)) throw eventNotFound();
		return new ApplicationStatus(applications.existsByUser_IdAndEvent_Id(userId, eventId));
	}

	public record ApplicationStatus(boolean applied) {}

	private static ApiException authenticationRequired() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
	}

	private static ApiException eventNotFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "행사를 찾을 수 없습니다.");
	}
}
