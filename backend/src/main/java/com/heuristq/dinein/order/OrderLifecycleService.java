package com.heuristq.dinein.order;

import com.heuristq.dinein.order.domain.OrderEntity;
import com.heuristq.dinein.order.domain.OrderRepository;
import com.heuristq.dinein.order.domain.OrderStateMachine;
import com.heuristq.dinein.order.domain.OrderStatus;
import com.heuristq.dinein.order.dto.OrderDtos.KitchenOrderView;
import com.heuristq.dinein.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/** Applies order state transitions: validation, timestamps, audit log line and realtime event. */
@Slf4j
@Service
public class OrderLifecycleService {

    private final OrderRepository orderRepository;
    private final OrderViewMapper viewMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderLifecycleService(OrderRepository orderRepository, OrderViewMapper viewMapper,
                                 ApplicationEventPublisher events, Clock clock) {
        this.orderRepository = orderRepository;
        this.viewMapper = viewMapper;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Moves a (locked) order to {@code to}. Must run inside the caller's transaction.
     *
     * @throws ApiException 409 when the transition is illegal
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transition(OrderEntity order, OrderStatus to, String actor) {
        OrderStatus from = order.getStatus();
        OrderStateMachine.assertTransition(from, to);
        Instant now = clock.instant();
        switch (to) {
            case CONFIRMED -> order.setPaidAt(order.getPaidAt() == null ? now : order.getPaidAt());
            case PREPARING -> order.setPreparingAt(now);
            case READY -> order.setReadyAt(now);
            case COMPLETED -> order.setCompletedAt(now);
            case CANCELLED -> order.setCancelledAt(now);
            default -> {
                // EXPIRED / PAYMENT_FAILED carry no dedicated timestamp; updated_at records the moment.
            }
        }
        order.setStatus(to);
        orderRepository.save(order);
        log.info("order.transition orderId={} orderNumber={} from={} to={} actor={}",
                order.getId(), order.getOrderNumber(), from, to, actor);
        // CONFIRMED carries the ticket for the kitchen; READY carries it so waiter screens can alert without a refetch.
        KitchenOrderView kitchenView = to == OrderStatus.CONFIRMED || to == OrderStatus.READY
                ? viewMapper.toKitchenView(order, viewMapper.tableLabel(order)) : null;
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), from, to, kitchenView));
    }

    /** Staff-initiated change (kitchen board / admin), restricted to {@code allowedTargets}. */
    @Transactional
    public KitchenOrderView changeByStaff(Long orderId, OrderStatus to, Set<OrderStatus> allowedTargets, String actor) {
        if (!allowedTargets.contains(to)) {
            throw ApiException.badRequest("STATUS_NOT_ALLOWED", "You cannot set status " + to + " here");
        }
        OrderEntity order = orderRepository.findByIdForUpdate(orderId).orElseThrow(() -> ApiException.notFound("Order"));
        if (order.getStatus() == to) {
            // Idempotent for double taps on the kitchen screen.
            return viewMapper.toKitchenView(order, viewMapper.tableLabel(order));
        }
        transition(order, to, actor);
        return viewMapper.toKitchenView(order, viewMapper.tableLabel(order));
    }
}
