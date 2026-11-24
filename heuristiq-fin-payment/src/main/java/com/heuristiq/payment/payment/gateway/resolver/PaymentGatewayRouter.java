package com.heuristiq.paymentservice.payment.gateway.resolver;

import com.heuristiq.paymentservice.config.PaymentGatewayRoutingProperties;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Decides which of the 4 supported gateways an online payment/wallet-topup routes through when
 * the caller doesn't specify one explicitly. Extracted out of PaymentService so PaymentRefundService
 * (and any other future collaborator needing the same "which gateway is this" question) doesn't
 * need to depend on the whole of PaymentService just for this.
 */
@Component
@RequiredArgsConstructor
public class PaymentGatewayRouter {

    private final PaymentGatewayRoutingProperties gatewayRoutingProperties;

    public PaymentGatewayType resolve(String rawGateway) {
        return (rawGateway == null || rawGateway.isBlank())
                ? defaultGatewayType()
                : PaymentGatewayType.fromValue(rawGateway);
    }

    /**
     * The platform-wide default gateway, read from {@code app.payment.default-gateway}
     * (env var {@code DEFAULT_PAYMENT_GATEWAY}) — NOT a hardcoded RAZORPAY literal. An operator
     * can switch every caller that doesn't specify a {@code gateway} explicitly over to a
     * different gateway with just a config change + restart, no code change or redeploy.
     */
    public PaymentGatewayType defaultGatewayType() {
        return PaymentGatewayType.fromValue(gatewayRoutingProperties.getDefaultGateway());
    }
}
