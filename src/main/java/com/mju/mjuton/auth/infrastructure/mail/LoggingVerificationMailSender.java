package com.mju.mjuton.auth.infrastructure.mail;

import com.mju.mjuton.auth.service.VerificationMailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mjuton.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
class LoggingVerificationMailSender implements VerificationMailSender {
	private static final Logger log = LoggerFactory.getLogger(LoggingVerificationMailSender.class);

	@Override
	public void send(String email, String code) {
		log.info("Development verification mail: email={}, code={}", email, code);
	}
}
