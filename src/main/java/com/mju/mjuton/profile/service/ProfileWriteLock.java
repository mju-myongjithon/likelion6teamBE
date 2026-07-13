package com.mju.mjuton.profile.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ProfileWriteLock {
	private final TransactionTemplate transactionTemplate;

	public ProfileWriteLock(PlatformTransactionManager transactionManager) {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public synchronized <T> T execute(Supplier<T> action) {
		return transactionTemplate.execute(status -> action.get());
	}
}
