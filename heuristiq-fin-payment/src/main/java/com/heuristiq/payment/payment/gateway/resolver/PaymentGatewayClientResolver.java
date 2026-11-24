package com.heuristiq.paymentservice.payment.gateway.resolver;

import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Picks the right per-gateway adapter for each of the 3 outbound ports, by {@code PaymentGatewayType}.
 *
 * DELIBERATE SIMPLIFICATION vs. the reference design this was ported from (2026_RailCab_PaymentAPI's
 * PaymentGatewayClientResolver): that resolver wraps every resolved client in a Resilience4j-backed
 * decorator (circuit breaker / bulkhead / retry) and supports ordered fallback-routing across
 * candidate gateways. Neither is ported here — this service has no Resilience4j dependency/config,
 * and adding one plus fallback-routing policy is disproportionate scope for "add 3 more gateways".
 * This resolver is just a {@code Map<PaymentGatewayType, XxxGatewayClient>} built from the list of
 * beans Spring already collects for each port (one {@code @Component} bean per gateway per port) —
 * Spring's standard "inject List<T> of all beans implementing T" pattern, nothing more.
 */
@Component
public class PaymentGatewayClientResolver {

    private final Map<PaymentGatewayType, CreateSessionGatewayClient> createSessionClients;
    private final Map<PaymentGatewayType, RefundGatewayClient> refundClients;
    private final Map<PaymentGatewayType, StatusCheckGatewayClient> statusCheckClients;

    public PaymentGatewayClientResolver(List<CreateSessionGatewayClient> createSessionGatewayClients,
                                         List<RefundGatewayClient> refundGatewayClients,
                                         List<StatusCheckGatewayClient> statusCheckGatewayClients) {
        this.createSessionClients = index(createSessionGatewayClients, CreateSessionGatewayClient::getGatewayType);
        this.refundClients = index(refundGatewayClients, RefundGatewayClient::getGatewayType);
        this.statusCheckClients = index(statusCheckGatewayClients, StatusCheckGatewayClient::getGatewayType);
    }

    public CreateSessionGatewayClient resolveCreateSession(PaymentGatewayType gatewayType) {
        CreateSessionGatewayClient client = createSessionClients.get(gatewayType);
        if (client == null) {
            throw new PaymentServiceException("Unsupported or unconfigured payment gateway for checkout: " + gatewayType);
        }
        return client;
    }

    public RefundGatewayClient resolveRefund(PaymentGatewayType gatewayType) {
        RefundGatewayClient client = refundClients.get(gatewayType);
        if (client == null) {
            throw new PaymentServiceException("Unsupported or unconfigured payment gateway for refund: " + gatewayType);
        }
        return client;
    }

    public StatusCheckGatewayClient resolveStatusCheck(PaymentGatewayType gatewayType) {
        StatusCheckGatewayClient client = statusCheckClients.get(gatewayType);
        if (client == null) {
            throw new PaymentServiceException("Unsupported or unconfigured payment gateway for status-check: " + gatewayType);
        }
        return client;
    }

    private <T> Map<PaymentGatewayType, T> index(List<T> clients, Function<T, PaymentGatewayType> keyFn) {
        return clients.stream().collect(Collectors.toMap(keyFn, Function.identity()));
    }
}
