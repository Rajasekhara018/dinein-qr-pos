package com.railcab.nms.application.port.in;

import com.railcab.nms.domain.model.NotificationMessage;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;

/**
 * Port the web adapter depends on instead of the raw repository. Kept separate from
 * {@link NotificationMessageService} (which owns the write-side reserve/complete idempotency
 * ledger) so each class has one reason to change.
 */
public interface NotificationMessageQueryUseCase {

    PageResult<NotificationMessage> list(NotificationMessageStatus status, String correlationId, PageQuery pageQuery);
}
