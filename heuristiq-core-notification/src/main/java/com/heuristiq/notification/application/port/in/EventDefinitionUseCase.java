package com.railcab.nms.application.port.in;

import java.util.List;
import java.util.Map;

import com.railcab.nms.application.dto.EventConfigResponse;
import com.railcab.nms.domain.model.NotificationEventDefinition;

/** Port the web adapter depends on instead of the concrete event-definition service. */
public interface EventDefinitionUseCase {

    List<NotificationEventDefinition> findAllActive();

    NotificationEventDefinition create(NotificationEventDefinition event);

    NotificationEventDefinition update(String id, NotificationEventDefinition updated);

    void delete(String id);

    List<String> attributesFor(String eventValue);

    Map<String, EventConfigResponse> config();

    List<NotificationEventDefinition> resetDefaults();
}
