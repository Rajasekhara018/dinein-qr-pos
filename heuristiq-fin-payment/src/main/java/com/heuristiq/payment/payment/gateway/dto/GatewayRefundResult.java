package com.heuristiq.payment.payment.gateway.dto;

/** Generic result of a {@link com.heuristiq.payment.payment.gateway.port.RefundGatewayClient} refund call. */
public record GatewayRefundResult(String refundId, String status) {
}
