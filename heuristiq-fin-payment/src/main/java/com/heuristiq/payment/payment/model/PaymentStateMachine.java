package com.heuristiq.paymentservice.payment.model;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.heuristiq.paymentservice.payment.model.PaymentStatus.*;

/**
 * The legal status moves of a {@link Payment}. Every code path that changes a payment status (webhook,
 * status poll, settlement, refund, cash capture) goes through {@link #apply} so that a late or replayed
 * event can never move a payment backwards, for example a late {@code payment.failed} after CAPTURED.
 *
 * <p>FAILED, EXPIRED and CANCELLED may still move to CAPTURED: the gateway is the source of truth for
 * money received, and a customer can retry on the same order after a failed attempt or pay after the
 * poller expired the payment. REFUNDED, REVERSED and AMOUNT_MISMATCH are terminal.
 */
@Slf4j
public final class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> LEGAL = new EnumMap<>(PaymentStatus.class);

    static {
        LEGAL.put(INITIATED, EnumSet.of(AUTHORIZED, CAPTURED, FAILED, CANCELLED, EXPIRED, AMOUNT_MISMATCH));
        LEGAL.put(AUTHORIZED, EnumSet.of(CAPTURED, FAILED, CANCELLED, EXPIRED, AMOUNT_MISMATCH));
        LEGAL.put(CAPTURED, EnumSet.of(SETTLED, REFUNDED, PARTIALLY_REFUNDED, REVERSED));
        LEGAL.put(SETTLED, EnumSet.of(REFUNDED, PARTIALLY_REFUNDED, REVERSED));
        LEGAL.put(PARTIALLY_REFUNDED, EnumSet.of(REFUNDED, REVERSED));
        LEGAL.put(FAILED, EnumSet.of(CAPTURED));
        LEGAL.put(EXPIRED, EnumSet.of(CAPTURED));
        LEGAL.put(CANCELLED, EnumSet.of(CAPTURED));
        LEGAL.put(REVERSED, EnumSet.noneOf(PaymentStatus.class));
        LEGAL.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        LEGAL.put(AMOUNT_MISMATCH, EnumSet.noneOf(PaymentStatus.class));
    }

    private PaymentStateMachine() {
    }

    /** True when {@code from -> to} is a legal move. A move to the same status is not a transition. */
    public static boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        return LEGAL.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Moves the payment to {@code to} (and stamps {@code updatedAt}) only when the move is legal.
     * An illegal move is logged with ids only and leaves the payment untouched.
     *
     * @return true when the status was changed
     */
    public static boolean apply(Payment payment, PaymentStatus to) {
        PaymentStatus from = payment.getStatus();
        if (!canTransition(from, to)) {
            log.warn("Ignoring illegal payment status move {} -> {} for payment {}",
                    from, to, payment.getInternalPaymentId());
            return false;
        }
        payment.setStatus(to);
        payment.setUpdatedAt(LocalDateTime.now());
        return true;
    }
}
