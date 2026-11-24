package com.heuristiq.payment.gatewayaudit.decorator;

import com.heuristiq.payment.gatewayaudit.GatewayCallTracer;
import com.heuristiq.payment.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.payment.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Wraps every gateway adapter bean (any bean implementing one of the three gateway ports) in its
 * audit decorator, so no individual gateway class is edited and
 * {@code PaymentGatewayClientResolver} transparently receives the logged versions. The tracer is
 * looked up lazily to avoid initialising it while post-processors are still being created.
 */
public class GatewayCallDecoratorPostProcessor implements BeanPostProcessor {

    private final ObjectProvider<GatewayCallTracer> tracer;

    public GatewayCallDecoratorPostProcessor(ObjectProvider<GatewayCallTracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CreateSessionGatewayClientAuditDecorator
                || bean instanceof RefundGatewayClientAuditDecorator
                || bean instanceof StatusCheckGatewayClientAuditDecorator) {
            return bean;
        }
        if (bean instanceof CreateSessionGatewayClient client) {
            return new CreateSessionGatewayClientAuditDecorator(client, tracer::getObject);
        }
        if (bean instanceof RefundGatewayClient client) {
            return new RefundGatewayClientAuditDecorator(client, tracer::getObject);
        }
        if (bean instanceof StatusCheckGatewayClient client) {
            return new StatusCheckGatewayClientAuditDecorator(client, tracer::getObject);
        }
        return bean;
    }
}
