package com.heuristiq.paymentservice.payment.gateway.dto;

/** Generic result of a {@link com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient} refund call. */
public record GatewayRefundResult(String refundId, String status) {
}
