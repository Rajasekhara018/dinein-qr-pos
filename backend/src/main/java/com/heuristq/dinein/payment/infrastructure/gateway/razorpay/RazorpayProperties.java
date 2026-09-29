package com.heuristq.dinein.payment.infrastructure.gateway.razorpay;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payments.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret) {

    public boolean isConfigured() {
        return notBlank(keyId) && notBlank(keySecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
