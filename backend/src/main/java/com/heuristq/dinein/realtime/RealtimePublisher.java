package com.heuristq.dinein.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Slf4j
@Component
public class RealtimePublisher {

    public static final String KITCHEN_TOPIC = "/topic/kitchen/orders";
    public static final String MENU_TOPIC = "/topic/menu";
    public static final String ORDER_TOPIC_PREFIX = "/topic/orders/";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void menuUpdated() {
        send(MENU_TOPIC, new RealtimeEvent(RealtimeEvent.MENU_UPDATED, null, null, null, clock.instant()));
    }

    public void toKitchen(String type, Long orderId, String status, Object order) {
        send(KITCHEN_TOPIC, new RealtimeEvent(type, orderId, status, order, clock.instant()));
    }

    public void toGuest(Long orderId, String status) {
        send(ORDER_TOPIC_PREFIX + orderId,
                new RealtimeEvent(RealtimeEvent.ORDER_STATUS_CHANGED, orderId, status, null, clock.instant()));
    }

    private void send(String destination, RealtimeEvent event) {
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (RuntimeException e) {
            // Realtime is best effort: clients refetch on reconnect, so never fail the business operation.
            log.warn("realtime.send_failed destination={} type={}", destination, event.type(), e);
        }
    }
}
