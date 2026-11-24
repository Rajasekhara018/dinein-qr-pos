package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Toucan bearer-token-authenticated checkout/status config. Property shape ported from the
 * reference design's ToucanGatewayProperties (payment.gateway.toucan.*). merchant.token/terminal/
 * merchantNumber have NO literal defaults — these are real credentials and must come from env
 * vars only.
 */
@Component
@ConfigurationProperties(prefix = "payment.gateway.toucan")
@Getter
@Setter
public class ToucanGatewayProperties {
    private String baseUrl = "https://pay.toucanpay.in";
    private String merchantToken;
    private String merchantTerminal;
    private String merchantNumber;
    private String currency = "INR";
}
