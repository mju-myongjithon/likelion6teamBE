package com.mju.mjuton.auth.service;

public interface VerificationMailSender {
	void send(String email, String code);
}
