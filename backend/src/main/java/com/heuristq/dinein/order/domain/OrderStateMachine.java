package com.heuristq.dinein.order.domain;

import com.heuristq.dinein.shared.exception.ApiException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.heuristq.dinein.order.domain.OrderStatus.CANCELLED;
import static com.heuristq.dinein.order.domain.OrderStatus.COMPLETED;
import static com.heuristq.dinein.order.domain.OrderStatus.CONFIRMED;
import static com.heuristq.dinein.order.domain.OrderStatus.EXPIRED;
import static com.heuristq.dinein.order.domain.OrderStatus.PAYMENT_FAILED;
import static com.heuristq.dinein.order.domain.OrderStatus.PENDING_PAYMENT;
import static com.heuristq.dinein.order.domain.OrderStatus.PREPARING;
import static com.heuristq.dinein.order.domain.OrderStatus.READY;

/**
 * The single source of truth for legal order transitions.
 *
 * <pre>
 * PENDING_PAYMENT -> CONFIRMED -> PREPARING -> READY -> COMPLETED
 * PENDING_PAYMENT -> EXPIRED | PAYMENT_FAILED
 * EXPIRED | PAYMENT_FAILED -> CONFIRMED      (a capture that arrives late still reaches the kitchen)
 * CONFIRMED | PREPARING -> CANCELLED         (admin cancel, full refund)
 * READY -> PREPARING                        (kitchen "Recall": marked ready too early, still being worked on)
 * </pre>
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(PENDING_PAYMENT, EnumSet.of(CONFIRMED, EXPIRED, PAYMENT_FAILED));
        ALLOWED.put(EXPIRED, EnumSet.of(CONFIRMED));
        ALLOWED.put(PAYMENT_FAILED, EnumSet.of(CONFIRMED));
        ALLOWED.put(CONFIRMED, EnumSet.of(PREPARING, CANCELLED));
        ALLOWED.put(PREPARING, EnumSet.of(READY, CANCELLED));
        ALLOWED.put(READY, EnumSet.of(COMPLETED, PREPARING));
        ALLOWED.put(COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws ApiException 409 ILLEGAL_TRANSITION when the move is not allowed */
    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw ApiException.conflict("ILLEGAL_TRANSITION",
                    "Order cannot move from " + from + " to " + to);
        }
    }
}
