package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.realtime.RealtimeEvent;
import com.heuristq.dinein.realtime.RealtimePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pushes order changes after commit: to the owning guest's topic and to {@code /topic/kitchen/{restaurantId}/orders},
 * which kitchen screens and waiter screens both subscribe to. The kitchen payload rides on ORDER_CONFIRMED and on the
 * ORDER_STATUS_CHANGED event for READY (what waiters act on). Also pushes to the public, login-free customer display
 * board ({@code /topic/display/{restaurantId}}) whenever an order enters/leaves PREPARING or READY.
 */
@Component
public class OrderRealtimeListener {

    /** Statuses the customer display board cares about: appears in PREPARING, moves to READY, then disappears. */
    private static final Set<OrderStatus> DISPLAY_RELEVANT =
            EnumSet.of(OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    private final RealtimePublisher publisher;

    public OrderRealtimeListener(RealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus to = event.to();
        publisher.toGuest(event.orderId(), to.name());
        if (to == OrderStatus.CONFIRMED) {
            publisher.toKitchen(event.restaurantId(), RealtimeEvent.ORDER_CONFIRMED, event.orderId(), to.name(),
                    event.kitchenView());
        } else if (to == OrderStatus.CANCELLED) {
            publisher.toKitchen(event.restaurantId(), RealtimeEvent.ORDER_CANCELLED, event.orderId(), to.name(), null);
        } else if (OrderStatus.KITCHEN_VISIBLE.contains(to) || to == OrderStatus.COMPLETED) {
            publisher.toKitchen(event.restaurantId(), RealtimeEvent.ORDER_STATUS_CHANGED, event.orderId(), to.name(),
                    to == OrderStatus.READY ? event.kitchenView() : null);
        }
        if (DISPLAY_RELEVANT.contains(to)) {
            publisher.toDisplay(event.restaurantId(), event.displayToken(), to.name());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPriorityChanged(OrderPriorityChangedEvent event) {
        publisher.toKitchen(event.restaurantId(), RealtimeEvent.ORDER_PRIORITY_CHANGED, event.orderId(), null,
                event.kitchenView());
    }
}
