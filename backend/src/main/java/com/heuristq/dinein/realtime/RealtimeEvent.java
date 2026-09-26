package com.heuristq.dinein.realtime;

import java.time.Instant;

/**
 * Notification pushed over STOMP. Clients treat it as a hint and refetch REST state, which stays the source of truth.
 *
 * @param type    ORDER_CONFIRMED, ORDER_STATUS_CHANGED, ORDER_CANCELLED or MENU_UPDATED
 * @param orderId set for order events
 * @param status  new order status for order events
 * @param order   full order payload (kitchen view) for ORDER_CONFIRMED and for ORDER_STATUS_CHANGED to READY
 */
public record RealtimeEvent(String type, Long orderId, String status, Object order, Instant at) {

    public static final String ORDER_CONFIRMED = "ORDER_CONFIRMED";
    public static final String ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED";
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";
    public static final String MENU_UPDATED = "MENU_UPDATED";
}
