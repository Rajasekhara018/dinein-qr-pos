package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * app.payment.default-gateway — which of the 4 supported gateways (RAZORPAY/PAYU/TOUCAN/PINELABS)
 * an online (non-CASH) payment is routed to when the caller's POST /payments request doesn't
 * specify a {@code gateway} explicitly. Existing callers (Ride/Dispatch services) never send
 * {@code gateway} today, so this property is effectively "the platform-wide gateway" until/unless
 * callers start opting into a specific one per-request.
 *
 * Deliberately a config property rather than a hardcoded RAZORPAY literal in PaymentService: an
 * operator can switch the platform-wide default gateway (e.g. DEFAULT_PAYMENT_GATEWAY=PAYU) with
 * a env var change + restart, no code change or redeploy required.
 */
@Component
@ConfigurationProperties(prefix = "app.payment")
@Getter
@Setter
public class PaymentGatewayRoutingProperties {
    private String defaultGateway = "RAZORPAY";
}
