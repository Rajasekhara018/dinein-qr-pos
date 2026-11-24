package com.heuristiq.paymentservice.payment.gateway.dto;

import com.heuristiq.paymentservice.payment.dto.RazorpayOrderDetails;

import java.util.Map;

/**
 * What a {@link com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient} hands
 * back after starting a checkout session with its gateway.
 *
 * {@code razorpayOrder} is populated ONLY by the Razorpay adapter, so that
 * {@code PaymentResponse.razorpayOrder} — a field existing Ride/Dispatch-service callers already
 * consume verbatim — keeps its exact pre-existing shape. Every other gateway (PayU, Toucan,
 * PineLabs) instead populates the generic {@code gatewaySession} map with whatever fields its own
 * checkout flow needs (hosted-form fields + action URL for PayU, a redirect URL for
 * Toucan/PineLabs) — new callers integrating those gateways read from there.
 */
public record GatewaySessionResult(
        String providerOrderId,
        RazorpayOrderDetails razorpayOrder,
        Map<String, Object> gatewaySession
) {
    public static GatewaySessionResult ofRazorpay(String providerOrderId, RazorpayOrderDetails details) {
        return new GatewaySessionResult(providerOrderId, details, null);
    }

    public static GatewaySessionResult ofGeneric(String providerOrderId, Map<String, Object> gatewaySession) {
        return new GatewaySessionResult(providerOrderId, null, gatewaySession);
    }
}
