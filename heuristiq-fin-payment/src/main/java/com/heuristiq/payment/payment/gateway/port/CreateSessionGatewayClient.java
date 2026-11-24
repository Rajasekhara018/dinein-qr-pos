package com.heuristiq.paymentservice.payment.gateway.port;

import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.model.Payment;

/**
 * Starts a checkout session with a specific gateway for a just-created {@link Payment} (still
 * INITIATED, not yet saved with a providerOrderId). One implementation per supported gateway;
 * {@link com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver} picks
 * the right one by {@link #getGatewayType()}.
 */
public interface CreateSessionGatewayClient {

    PaymentGatewayType getGatewayType();

    GatewaySessionResult createSession(Payment payment);
}
