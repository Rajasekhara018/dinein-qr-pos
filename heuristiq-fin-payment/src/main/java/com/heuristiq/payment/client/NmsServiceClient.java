package com.heuristiq.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.client.dto.NotificationRequest;
import com.heuristiq.payment.events.DurableEventOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class NmsServiceClient {
    private final DurableEventOutbox outbox;
    private final ObjectMapper mapper;
    @Value("${rider.notifications.topic:railcab-notification-events}")
    private String topic;
    @Transactional
    public void notify(NotificationRequest request) {
        String id = UUID.randomUUID().toString();
        var json = mapper.createObjectNode();
        json.put("messageID", id);
        json.set("requestObject", mapper.valueToTree(request));
        String key = request.getVariables() == null
                ? id : request.getVariables().getOrDefault("rideId", id);
        outbox.enqueue(id, topic, key, json.toString());
    }
}
