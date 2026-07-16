package com.mju.mjuton.recruitment.domain;

public enum JoinRequestStatus {
	PENDING,  // 대기 — 방장의 결정 전
	APPROVED, // 승인 — 채팅방 멤버로 승격됨
	REJECTED  // 거절 — 재신청 가능
}
