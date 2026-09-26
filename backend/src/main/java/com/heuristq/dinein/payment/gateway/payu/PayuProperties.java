package com.heuristq.dinein.payment.gateway.payu;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param baseUrl        {@code https://test.payu.in} (test) or {@code https://secure.payu.in} (live)
 * @param postServiceUrl {@code https://test.info.payu.in/merchant/postservice.php?form=2} or the live equivalent
 * @param defaultEmail   PayU requires an email; guests don't give one, so a restaurant address is used
 */
@ConfigurationProperties(prefix = "app.payments.payu")
public record PayuProperties(
        String key,
        String salt,
        @DefaultValue("https://test.payu.in") String baseUrl,
        @DefaultValue("https://test.info.payu.in/merchant/postservice.php?form=2") String postServiceUrl,
        @DefaultValue("orders@example.com") String defaultEmail) {

    public boolean isConfigured() {
        return key != null && !key.isBlank() && salt != null && !salt.isBlank();
    }
}
