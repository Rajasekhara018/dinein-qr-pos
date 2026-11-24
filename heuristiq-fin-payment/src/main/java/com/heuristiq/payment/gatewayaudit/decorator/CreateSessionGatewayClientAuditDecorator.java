package com.heuristiq.paymentservice.gatewayaudit.decorator;

import com.heuristiq.paymentservice.gatewayaudit.GatewayCallTracer;
import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;

import java.util.function.Supplier;

/** Logs every {@link CreateSessionGatewayClient#createSession} call of the wrapped gateway adapter. */
public class CreateSessionGatewayClientAuditDecorator implements CreateSessionGatewayClient {

    private final CreateSessionGatewayClient delegate;
    private final Supplier<GatewayCallTracer> tracer;

    public CreateSessionGatewayClientAuditDecorator(CreateSessionGatewayClient delegate, Supplier<GatewayCallTracer> tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public PaymentGatewayType getGatewayType() {
        return delegate.getGatewayType();
    }

    @Override
    public GatewaySessionResult createSession(Payment payment) {
        return tracer.get().trace(delegate.getGatewayType(), GatewayCallOperation.CREATE_SESSION,
                payment.getInternalPaymentId(), payment.getIdempotencyKey(), payment.getProviderOrderId(),
                GatewaySessionResult::providerOrderId, () -> delegate.createSession(payment));
    }
}
