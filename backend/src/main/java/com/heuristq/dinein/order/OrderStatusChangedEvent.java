package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;

/**
 * Raised inside the transaction; realtime notifications go out only after it commits. {@code kitchenView} is set for
 * transitions to CONFIRMED and READY. {@code restaurantId} scopes the realtime broadcast to that tenant only.
 */
public record OrderStatusChangedEvent(Long orderId, Long restaurantId, OrderStatus from, OrderStatus to,
                                      KitchenOrderView kitchenView) {
}
