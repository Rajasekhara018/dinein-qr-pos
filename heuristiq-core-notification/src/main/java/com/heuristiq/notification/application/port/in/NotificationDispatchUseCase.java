package com.heuristiq.notification.application.port.in;

import java.util.List;

import com.heuristiq.notification.application.dto.NotificationDispatchResult;
import com.heuristiq.notification.application.dto.NotificationRequest;

/** Port the web/Kafka inbound adapters depend on instead of the concrete dispatch service. */
public interface NotificationDispatchUseCase {

    List<NotificationDispatchResult> dispatch(NotificationRequest request);
}
