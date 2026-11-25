package com.heuristiq.payment.gatewayaudit.decorator;

import com.heuristiq.payment.gatewayaudit.GatewayCallTracer;
import com.heuristiq.payment.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.payment.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.payment.payment.model.Payment;

import java.util.function.Supplier;

/** Logs every {@link StatusCheckGatewayClient#fetchStatus} call of the wrapped gateway adapter. */
public class StatusCheckGatewayClientAuditDecorator implements StatusCheckGatewayClient {

    private final StatusCheckGatewayClient delegate;
    private final Supplier<GatewayCallTracer> tracer;

    public StatusCheckGatewayClientAuditDecorator(StatusCheckGatewayClient delegate, Supplier<GatewayCallTracer> tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return delegate.getGatewayType();
    }

    @Override
    public GatewayStatusResult fetchStatus(Payment payment) {
        return tracer.get().trace(delegate.getGatewayType(), GatewayCallOperation.STATUS_CHECK,
                payment.getInternalPaymentId(), payment.getProviderOrderId(), payment.getProviderOrderId(),
                GatewayStatusResult::providerPaymentId, () -> delegate.fetchStatus(payment));
    }
}
