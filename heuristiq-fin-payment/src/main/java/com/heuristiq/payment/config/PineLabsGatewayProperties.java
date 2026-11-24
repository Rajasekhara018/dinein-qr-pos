package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Pine Labs config. Checkout/status both need an OAuth-style access token first (see
 * PineLabsAuthGatewayClient) — client-credentials grant against clientId/clientSecret. Property
 * shape ported from the reference design's PineLabsGatewayProperties (payment.gateway.pinelabs.*).
 * merchantId/clientId/clientSecret have NO literal defaults.
 */
@Component
@ConfigurationProperties(prefix = "payment.gateway.pinelabs")
@Getter
@Setter
public class PineLabsGatewayProperties {
    private String baseUrl = "https://pluraluat.v2.pinepg.in";
    private String merchantId;
    private String clientId;
    private String clientSecret;
    private String name = "Rider";
    /** Browser is redirected here (paymentId appended) after checkout completes. */
    private String callbackUrl;
}
