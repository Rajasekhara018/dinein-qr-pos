package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.domain.model.NotificationMessage;
import com.heuristiq.notification.domain.model.NotificationMessageStatus;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;

/**
 * Port the web adapter depends on instead of the raw repository. Kept separate from
 * {@link NotificationMessageService} (which owns the write-side reserve/complete idempotency
 * ledger) so each class has one reason to change.
 */
public interface NotificationMessageQueryUseCase {

    PageResult<NotificationMessage> list(NotificationMessageStatus status, String correlationId, PageQuery pageQuery);
}
