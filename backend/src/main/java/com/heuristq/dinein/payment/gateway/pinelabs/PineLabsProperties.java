package com.heuristq.dinein.payment.gateway.pinelabs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.pinelabs")
public record PineLabsProperties(String merchantId, String clientId, String clientSecret, String webhookSecret,
                                 String baseUrl) {
}
