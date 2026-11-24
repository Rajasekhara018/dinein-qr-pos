package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;

/**
 * Raised inside the transaction; realtime notifications go out only after it commits. {@code kitchenView} is set for
 * transitions to CONFIRMED and READY. {@code restaurantId} scopes the realtime broadcast to that tenant only.
 * {@code displayToken} feeds the public customer-facing "order ready" screen (see {@code OrderRealtimeListener}).
 */
public record OrderStatusChangedEvent(Long orderId, Long restaurantId, int displayToken, OrderStatus from,
                                      OrderStatus to, KitchenOrderView kitchenView) {
}
