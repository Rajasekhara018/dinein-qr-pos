package com.heuristiq.payment.gatewayaudit;

import com.heuristiq.payment.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.payment.gatewayaudit.model.GatewayCallOutcome;

/** What the tracer saw of one gateway call; metadata only, never a body or credential. */
public record GatewayCallObservation(String gateway, GatewayCallOperation operation, String paymentId,
                                     String reference, String gatewayRef, Integer httpStatus, String errorClass,
                                     GatewayCallOutcome outcome, String failureReason, long latencyMs,
                                     String correlationId) {
}
