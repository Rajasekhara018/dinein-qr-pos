package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.realtime.RealtimeEvent;
import com.heuristq.dinein.realtime.RealtimePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes order changes after commit: to the owning guest's topic and to {@code /topic/kitchen/orders}, which kitchen
 * screens and waiter screens both subscribe to. The kitchen payload rides on ORDER_CONFIRMED and on the
 * ORDER_STATUS_CHANGED event for READY (what waiters act on).
 */
@Component
public class OrderRealtimeListener {

    private final RealtimePublisher publisher;

    public OrderRealtimeListener(RealtimePublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        OrderStatus to = event.to();
        publisher.toGuest(event.orderId(), to.name());
        if (to == OrderStatus.CONFIRMED) {
            publisher.toKitchen(RealtimeEvent.ORDER_CONFIRMED, event.orderId(), to.name(), event.kitchenView());
        } else if (to == OrderStatus.CANCELLED) {
            publisher.toKitchen(RealtimeEvent.ORDER_CANCELLED, event.orderId(), to.name(), null);
        } else if (OrderStatus.KITCHEN_VISIBLE.contains(to) || to == OrderStatus.COMPLETED) {
            publisher.toKitchen(RealtimeEvent.ORDER_STATUS_CHANGED, event.orderId(), to.name(),
                    to == OrderStatus.READY ? event.kitchenView() : null);
        }
    }
}
