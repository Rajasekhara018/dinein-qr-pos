package com.heuristq.dinein.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Entry point for the rest of the application. {@link #send} returns immediately: inside a transaction the request
 * is queued until after commit (and dropped on rollback), then delivered on the notification executor. It never
 * throws, so notifications can never break ordering or payments.
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationDispatcher dispatcher;
    private final TaskExecutor executor;

    public NotificationService(NotificationDispatcher dispatcher,
                               @Qualifier(NotificationAsyncConfig.EXECUTOR) TaskExecutor executor) {
        this.dispatcher = dispatcher;
        this.executor = executor;
    }

    public void send(NotificationRequest request) {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(request);
                    }
                });
            } else {
                submit(request);
            }
        } catch (RuntimeException e) {
            log.warn("notification.enqueue_failed event={} orderId={} error={}", request.event(), request.orderId(),
                    e.getMessage());
        }
    }

    private void submit(NotificationRequest request) {
        try {
            executor.execute(() -> dispatcher.dispatch(request));
        } catch (RuntimeException e) {
            log.warn("notification.enqueue_failed event={} orderId={} error={}", request.event(), request.orderId(),
                    e.getMessage());
        }
    }
}
