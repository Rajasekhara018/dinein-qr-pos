package com.heuristiq.paymentservice.gatewayaudit.decorator;

import com.heuristiq.paymentservice.gatewayaudit.GatewayCallTracer;
import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;

import java.util.function.Supplier;
import java.math.BigDecimal;

/** Logs every refund call of the wrapped gateway adapter (both overloads, each exactly once). */
public class RefundGatewayClientAuditDecorator implements RefundGatewayClient {

    private final RefundGatewayClient delegate;
    private final Supplier<GatewayCallTracer> tracer;

    public RefundGatewayClientAuditDecorator(RefundGatewayClient delegate, Supplier<GatewayCallTracer> tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return delegate.getGatewayType();
    }

    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason) {
        return tracer.get().trace(delegate.getGatewayType(), GatewayCallOperation.REFUND,
                payment.getInternalPaymentId(), null, payment.getProviderPaymentId(),
                GatewayRefundResult::refundId, () -> delegate.refund(payment, amount, reason));
    }

    @Override
    public GatewayRefundResult refund(Payment payment, BigDecimal amount, String reason, String reference) {
        return tracer.get().trace(delegate.getGatewayType(), GatewayCallOperation.REFUND,
                payment.getInternalPaymentId(), reference, payment.getProviderPaymentId(),
                GatewayRefundResult::refundId, () -> delegate.refund(payment, amount, reason, reference));
    }
}
