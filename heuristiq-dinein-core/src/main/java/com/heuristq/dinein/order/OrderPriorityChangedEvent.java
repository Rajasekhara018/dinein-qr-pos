package com.heuristq.dinein.order;

import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;

/** Raised inside the transaction; the realtime push goes out only after it commits. */
public record OrderPriorityChangedEvent(Long orderId, Long restaurantId, KitchenOrderView kitchenView) {
}
