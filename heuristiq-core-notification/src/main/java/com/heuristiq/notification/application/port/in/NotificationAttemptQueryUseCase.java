package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/** Read side for the per-attempt delivery records (admin failure analysis). */
public interface NotificationAttemptQueryUseCase {

    /** Every attempt of one message across channels and recipients, oldest first. */
    PageResult<NotificationAttempt> findByMessageId(String messageId, PageQuery pageQuery);

    /** Every attempt that shares a correlation id, oldest first. */
    PageResult<NotificationAttempt> findByCorrelationId(String correlationId, PageQuery pageQuery);
}
