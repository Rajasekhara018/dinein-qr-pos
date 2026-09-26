package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.realtime.RealtimeEvent;
import com.heuristq.dinein.realtime.RealtimePublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

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
            publisher.toKitchen(RealtimeEvent.ORDER_STATUS_CHANGED, event.orderId(), to.name(), null);
        }
    }
}
