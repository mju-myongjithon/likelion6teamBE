package com.mju.mjuton.auth.infrastructure.mail;

import com.mju.mjuton.auth.service.VerificationMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mjuton.mail", name = "enabled", havingValue = "true")
class SmtpVerificationMailSender implements VerificationMailSender {
	private final JavaMailSender mailSender;
	private final String from;

	SmtpVerificationMailSender(JavaMailSender mailSender,
			@Value("${mjuton.mail.from}") String from) {
		this.mailSender = mailSender;
		this.from = from;
	}

	@Override
	public void send(String email, String code) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(from);
		message.setTo(email);
		message.setSubject("[MJU-ton] 이메일 인증번호");
		message.setText("인증번호는 " + code + " 입니다. 5분 안에 입력해 주세요.");
		mailSender.send(message);
	}
}
