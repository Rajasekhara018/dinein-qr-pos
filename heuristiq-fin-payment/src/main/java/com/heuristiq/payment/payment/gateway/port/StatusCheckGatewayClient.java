package com.heuristiq.paymentservice.payment.gateway.port;

import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.model.Payment;

/**
 * Polls a specific gateway for the current status of a payment it holds a providerOrderId/
 * providerPaymentId for. Implemented for all 4 gateways (each has a real status-check API in the
 * reference design), but NOT wired into any controller endpoint in this pass — see README's
 * scope-boundary note. Kept here so a future reconciliation/status-poll use case has real, tested
 * gateway integrations ready to call.
 */
public interface StatusCheckGatewayClient {

    PaymentGatewayType getGatewayType();

    GatewayStatusResult fetchStatus(Payment payment);
}
