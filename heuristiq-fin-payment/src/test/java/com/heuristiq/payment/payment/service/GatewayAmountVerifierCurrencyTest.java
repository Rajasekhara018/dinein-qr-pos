package com.heuristiq.payment.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.heuristiq.payment.config.MoneyProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The gateway check expects the configured currency (app.payment.currency), not a fixed INR. */
class GatewayAmountVerifierCurrencyTest {

    private static GatewayAmountVerifier verifier(String currency) {
        MoneyProperties properties = new MoneyProperties();
        properties.setCurrency(currency);
        return new GatewayAmountVerifier(properties);
    }

    @Test
    void theConfiguredCurrencyIsTheOneExpected() {
        GatewayAmountVerifier aed = verifier("AED");

        assertThat(aed.currency()).isEqualTo("AED");
        assertThat(aed.matches("t1", new BigDecimal("10.00"), "AED", 1000L, "aed")).isTrue();
        assertThat(aed.matches("t1", new BigDecimal("10.00"), "INR", 1000L, "INR")).isFalse();
        assertThat(aed.matches("t1", new BigDecimal("10.00"), null, 1000L, "AED")).isTrue();
        assertThat(verifier("INR").matches("t1", new BigDecimal("10.00"), "INR", 1000L, "INR")).isTrue();
        assertThat(verifier("INR").matches("t1", new BigDecimal("10.00"), "INR", 999L, "INR")).isFalse();
    }
}
