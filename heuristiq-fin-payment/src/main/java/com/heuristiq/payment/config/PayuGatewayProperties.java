package com.heuristiq.paymentservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PayU hosted-checkout config. Property shape ported from the reference design's
 * PayuGatewayProperties (payment.gateway.payu.*). key/salt/saltV1/saltV2 have NO literal
 * defaults — sandbox or production values must come from env vars only (see this service's
 * security requirement for gateway credentials, same discipline as RazorpayProperties'
 * key-secret/webhook-secret).
 */
@Component
@ConfigurationProperties(prefix = "payment.gateway.payu")
@Getter
@Setter
public class PayuGatewayProperties {
    /** Hosted-checkout form action URL the browser POSTs the signed fields to. */
    private String paymentUrl = "https://test.payu.in/_payment";
    /** PayU's server-to-server postservice.php endpoint, used for refund/verify_payment calls. */
    private String postServiceUrl = "https://test.payu.in/merchant/postservice.php?form=2";
    private String key;
    private String salt;
    private String saltV1;
    private String saltV2;
    private String successUrl;
    private String failureUrl;
    private String refundCallbackUrl;
    private String productInfo = "Rider ride payment";
}
