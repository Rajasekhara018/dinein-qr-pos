package com.heuristiq.payment.gatewayaudit.admin;

import com.heuristiq.payment.gatewayaudit.model.PaymentGatewayCall;

import java.time.Instant;
import java.time.ZoneOffset;

/** One logged gateway call as the internal admin API returns it. */
public record AdminGatewayCall(String id, String gateway, String operation, String paymentId, String reference,
                               String gatewayRef, Integer httpStatus, String errorClass, String outcome,
                               String failureReason, long latencyMs, int attempt, String correlationId,
                               Instant createdAt) {

    public static AdminGatewayCall from(PaymentGatewayCall c) {
        return new AdminGatewayCall(c.getId(), c.getGateway(), c.getOperation().name(), c.getPaymentId(),
                c.getReference(), c.getGatewayRef(), c.getHttpStatus(), c.getErrorClass(), c.getOutcome().name(),
                c.getFailureReason(), c.getLatencyMs(), c.getAttempt(), c.getCorrelationId(),
                c.getCreatedAt() == null ? null : c.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
