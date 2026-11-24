package com.heuristiq.paymentservice.payment.dto.admin;

import java.time.Instant;

/**
 * One step of a payment's lifecycle, from GET /api/v1/internal/admin/payments/{id}/transitions.
 *
 * @param source what drove the change: CHECKOUT, WEBHOOK-style gateway callbacks, STATUS_POLL, RECONCILE,
 *               SETTLEMENT, REFUND, PAYMENT_SERVICE, or DERIVED for steps reconstructed from the payment's
 *               own timestamps because the payment predates history recording
 */
public record AdminPaymentTransition(String fromStatus, String toStatus, String source, String reason,
                                     Instant transitionedAt) {
}
