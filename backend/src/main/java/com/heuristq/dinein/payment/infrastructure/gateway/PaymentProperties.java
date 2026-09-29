package com.heuristq.dinein.payment.infrastructure.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.payments.provider} selects the gateway used for new payments (existing ones keep their provider). */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(@DefaultValue("RAZORPAY") String provider) {
}
