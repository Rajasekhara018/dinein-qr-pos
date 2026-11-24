package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Same property names/env vars as the monolith's shared.config.RazorpayProperties. */
@Component
@ConfigurationProperties(prefix = "app.razorpay")
@Getter
@Setter
public class RazorpayProperties {
    private String keyId;
    private String keySecret;
    private String webhookSecret;

    /** Razorpay's REST API base URL — used for the direct refund call (RazorpayRefundClient). */
    private String baseUrl = "https://api.razorpay.com/v1";

    /** Upper bound in seconds for the blocking refund call (app.razorpay.refund-timeout-seconds). */
    private long refundTimeoutSeconds = 30;
}
