package com.heuristq.dinein.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Every per-restaurant topic is built from {@code restaurantId} (see {@link #kitchenTopic}, {@link #staffTopic},
 * {@link #waiterTopic}) so one tenant's kitchen tickets/notifications are never delivered to another's staff --
 * {@code StompAuthChannelInterceptor} enforces that the subscriber's own restaurant matches. Only {@code /topic/menu}
 * and the per-order guest topic are exempt: the menu topic carries no data (just "refetch"), and the guest topic is
 * already scoped by unguessable order id + ownership check.
 */
@Slf4j
@Component
public class RealtimePublisher {

    public static final String KITCHEN_TOPIC_PREFIX = "/topic/kitchen/";
    public static final String KITCHEN_TOPIC_SUFFIX = "/orders";
    public static final String MENU_TOPIC = "/topic/menu";
    public static final String ORDER_TOPIC_PREFIX = "/topic/orders/";
    public static final String STAFF_NOTIFICATIONS_PREFIX = "/topic/staff/";
    public static final String STAFF_NOTIFICATIONS_SUFFIX = "/notifications";
    public static final String WAITER_NOTIFICATIONS_PREFIX = "/topic/waiter/";
    public static final String WAITER_NOTIFICATIONS_SUFFIX = "/notifications";
    public static final String DISPLAY_TOPIC_PREFIX = "/topic/display/";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate, Clock clock) {
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public static String kitchenTopic(Long restaurantId) {
        return KITCHEN_TOPIC_PREFIX + restaurantId + KITCHEN_TOPIC_SUFFIX;
    }

    public static String staffNotificationsTopic(Long restaurantId) {
        return STAFF_NOTIFICATIONS_PREFIX + restaurantId + STAFF_NOTIFICATIONS_SUFFIX;
    }

    public static String waiterNotificationsTopic(Long restaurantId) {
        return WAITER_NOTIFICATIONS_PREFIX + restaurantId + WAITER_NOTIFICATIONS_SUFFIX;
    }

    public static String displayTopic(Long restaurantId) {
        return DISPLAY_TOPIC_PREFIX + restaurantId;
    }

    /** Order number/name-free by design: this topic is public (no login), shown on a screen in the dining area. */
    public record DisplayEvent(int displayToken, String status) {
    }

    public void menuUpdated() {
        send(MENU_TOPIC, new RealtimeEvent(RealtimeEvent.MENU_UPDATED, null, null, null, clock.instant()));
    }

    public void toKitchen(Long restaurantId, String type, Long orderId, String status, Object order) {
        send(kitchenTopic(restaurantId), new RealtimeEvent(type, orderId, status, order, clock.instant()));
    }

    public void toGuest(Long orderId, String status) {
        send(ORDER_TOPIC_PREFIX + orderId,
                new RealtimeEvent(RealtimeEvent.ORDER_STATUS_CHANGED, orderId, status, null, clock.instant()));
    }

    /** New in-app notification for owners/managers; the payload names the role or user it targets. */
    public void toStaffNotifications(Long restaurantId, Object notification) {
        String destination = staffNotificationsTopic(restaurantId);
        try {
            messagingTemplate.convertAndSend(destination, notification);
        } catch (RuntimeException e) {
            log.warn("realtime.send_failed destination={} type=NOTIFICATION", destination, e);
        }
    }

    /** New in-app notification for waiters (e.g. an order is ready to be served). */
    public void toWaiterNotifications(Long restaurantId, Object notification) {
        String destination = waiterNotificationsTopic(restaurantId);
        try {
            messagingTemplate.convertAndSend(destination, notification);
        } catch (RuntimeException e) {
            log.warn("realtime.send_failed destination={} type=NOTIFICATION", destination, e);
        }
    }

    /** Pushed whenever an order enters/leaves PREPARING or READY, or reaches a terminal state, for the display board. */
    public void toDisplay(Long restaurantId, int displayToken, String status) {
        String destination = displayTopic(restaurantId);
        try {
            messagingTemplate.convertAndSend(destination, new DisplayEvent(displayToken, status));
        } catch (RuntimeException e) {
            log.warn("realtime.send_failed destination={} type=DISPLAY", destination, e);
        }
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
