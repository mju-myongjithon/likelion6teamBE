package com.mju.mjuton.event.service;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class EventWriteLock {
	private final TransactionTemplate transactionTemplate;

	public EventWriteLock(PlatformTransactionManager transactionManager) {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public synchronized <T> T execute(Supplier<T> action) {
		return transactionTemplate.execute(status -> action.get());
	}
}
