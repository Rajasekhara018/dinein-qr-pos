package com.heuristiq.payment.shared.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AmountCurrencyTest {

    @Test
    void aCurrencyIsWrittenOnlyNextToASetAmount() {
        assertThat(AmountCurrency.of(new BigDecimal("10.00"), "INR")).isEqualTo("INR");
        assertThat(AmountCurrency.of(BigDecimal.ZERO, "AED")).isEqualTo("AED");
        assertThat(AmountCurrency.of(null, "INR")).isNull();
    }

    @Test
    void aRelatedRowsCurrencyIsUsedWhenItIsAThreeLetterCode_otherwiseTheFallback() {
        assertThat(AmountCurrency.of(new BigDecimal("5"), "AED", "INR")).isEqualTo("AED");
        assertThat(AmountCurrency.of(new BigDecimal("5"), "usd", "INR")).isEqualTo("USD");
        assertThat(AmountCurrency.of(new BigDecimal("5"), null, "INR")).isEqualTo("INR");
        assertThat(AmountCurrency.of(new BigDecimal("5"), "RUPEES", "INR")).isEqualTo("INR");
        assertThat(AmountCurrency.of(null, "AED", "INR")).isNull();
    }
}
