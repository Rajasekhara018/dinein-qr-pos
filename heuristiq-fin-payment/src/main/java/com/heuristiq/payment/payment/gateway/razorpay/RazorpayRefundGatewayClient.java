package com.heuristiq.paymentservice.payment.gateway.razorpay;

import com.heuristiq.paymentservice.client.RazorpayRefundClient;
import com.heuristiq.paymentservice.client.dto.RazorpayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import com.heuristiq.paymentservice.payment.service.GatewayAmountVerifier;

/**
 * Thin adapter over the existing {@link RazorpayRefundClient} (added in the prior pass — the
 * real REST call to POST /v1/payments/{id}/refund) so Razorpay's refund flow participates in the
 * same {@link RefundGatewayClient} port as PayU's, instead of PaymentService special-casing
 * Razorpay directly. No refund logic is duplicated here.
 */
@Component
@RequiredArgsConstructor
public class RazorpayRefundGatewayClient implements RefundGatewayClient {

    private final RazorpayRefundClient razorpayRefundClient;

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason) {
        return refund(payment, amount, reason, null);
    }

    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason, String reference) {
        long amountPaise = GatewayAmountVerifier.toPaise(amount);
        RazorpayRefundResult result = razorpayRefundClient.refund(
                payment.getProviderPaymentId(), amountPaise, reason, reference);
        return new GatewayRefundResult(result.refundId(), result.status());
    }
}
