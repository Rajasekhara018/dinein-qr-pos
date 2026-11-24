package com.railcab.nms.application.port.in;

import java.util.List;

import com.railcab.nms.application.dto.NotificationDispatchResult;
import com.railcab.nms.application.dto.NotificationRequest;

/** Port the web/Kafka inbound adapters depend on instead of the concrete dispatch service. */
public interface NotificationDispatchUseCase {

    List<NotificationDispatchResult> dispatch(NotificationRequest request);
}
