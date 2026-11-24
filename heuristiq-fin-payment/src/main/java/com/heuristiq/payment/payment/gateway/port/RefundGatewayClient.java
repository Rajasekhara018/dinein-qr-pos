package com.heuristiq.paymentservice.payment.gateway.port;

import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.model.Payment;
import java.math.BigDecimal;

/**
 * Calls a specific gateway's refund API for an already-CAPTURED/SETTLED {@link Payment}.
 * Only implemented for gateways that actually expose a refund API in the reference design this
 * was ported from — currently RAZORPAY and PAYU (TOUCAN/PINELABS have no refund adapter in that
 * reference either, so none is invented here).
 *
 * Per this service's established discipline (see PaymentService.refund): a failure MUST be
 * thrown, never swallowed, and MUST happen before any local Payment state mutation or wallet
 * credit.
 */
public interface RefundGatewayClient {

    PaymentGatewayType getGatewayType();

    GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason);

    /**
     * Refund keyed by a caller-chosen unique {@code reference}. Adapters whose gateway supports an
     * idempotency token override this so that repeating the call with the same reference cannot refund
     * twice; the default ignores the reference.
     */
    default GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason, String reference) {
        return refund(payment, amount, reason);
    }
}
