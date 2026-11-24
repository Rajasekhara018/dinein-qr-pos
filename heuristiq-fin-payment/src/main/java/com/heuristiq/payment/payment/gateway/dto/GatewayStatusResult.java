package com.heuristiq.paymentservice.payment.gateway.dto;

import java.math.BigDecimal;

/**
 * Generic result of a {@link com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient}
 * poll. Not currently wired into any controller endpoint in this pass (see README's "scoped out"
 * section) — the port/adapters exist so a future GET /payments/{id}/status use case has real,
 * tested gateway integrations to call rather than starting from scratch.
 */
public record GatewayStatusResult(
        String gateway,
        String providerOrderId,
        String providerPaymentId,
        /** Normalized to one of SUCCESS / FAILED / PENDING regardless of gateway. */
        String status,
        String rawProviderStatus,
        /** Amount the gateway reports as paid, in rupees; null when it reports none. */
        BigDecimal amount,
        String currency
) {
}
