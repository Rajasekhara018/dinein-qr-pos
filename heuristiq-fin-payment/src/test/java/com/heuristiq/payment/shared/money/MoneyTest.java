package com.heuristiq.payment.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The wire shape of an amount: written as {code, value}, read as that or as a legacy bare number. */
class MoneyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    record Holder(Money amount) {
    }

    @AfterEach
    void resetDefault() {
        MoneyDefaults.useCurrency(MoneyDefaults.FALLBACK_CURRENCY);
    }

    @Test
    void ofNormalisesTheCodeAndRoundsToTheCurrencysDecimals() {
        assertThat(Money.of(new BigDecimal("150.005"), "inr")).isEqualTo(new Money("INR", new BigDecimal("150.01")));
        assertThat(Money.of(new BigDecimal("1500.4"), "JPY").value()).isEqualByComparingTo("1500").hasScaleOf(0);
        assertThat(Money.of(new BigDecimal("1.2345"), "KWD").value()).hasScaleOf(3).isEqualByComparingTo("1.235");
        assertThat(Money.ofNullable(null, "INR")).isNull();
        assertThat(Money.valueOf(null)).isNull();
        assertThat(Money.codeOf(Money.of(BigDecimal.ONE, "AED"))).isEqualTo("AED");
        assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "XYZ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money("INR", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRefusesTooManyDecimalsInsteadOfRounding() {
        assertThat(Money.parse("INR", new BigDecimal("10.50")).value()).isEqualByComparingTo("10.50");
        assertThat(Money.parse("INR", new BigDecimal("10.500")).value()).hasScaleOf(2);
        assertThatThrownBy(() -> Money.parse("INR", new BigDecimal("10.555"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.parse("JPY", new BigDecimal("10.5"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.parse(" ", BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void writesTheObjectWithANumberValue() throws Exception {
        assertThat(mapper.writeValueAsString(new Holder(Money.of(new BigDecimal("150"), "INR"))))
                .isEqualTo("{\"amount\":{\"code\":\"INR\",\"value\":150.00}}");
        assertThat(mapper.writeValueAsString(new Holder(null))).isEqualTo("{\"amount\":null}");
    }

    @Test
    void readsTheObjectWithANumberOrStringValue() throws Exception {
        assertThat(mapper.readValue("{\"amount\":{\"code\":\"aed\",\"value\":99.5}}", Holder.class).amount())
                .isEqualTo(new Money("AED", new BigDecimal("99.50")));
        assertThat(mapper.readValue("{\"amount\":{\"code\":\"INR\",\"value\":\"12.30\"}}", Holder.class).amount().value())
                .isEqualByComparingTo("12.30");
        assertThat(mapper.readValue("{\"amount\":{\"code\":\"INR\",\"value\":null}}", Holder.class).amount()).isNull();
        assertThat(mapper.readValue("{\"amount\":null}", Holder.class).amount()).isNull();
    }

    @Test
    void readsTheLegacyBareNumberOrStringInTheConfiguredCurrency() throws Exception {
        assertThat(mapper.readValue("{\"amount\":150.5}", Holder.class).amount()).isEqualTo(new Money("INR", new BigDecimal("150.50")));
        assertThat(mapper.readValue("{\"amount\":\"75\"}", Holder.class).amount()).isEqualTo(new Money("INR", new BigDecimal("75.00")));
        MoneyDefaults.useCurrency("aed");
        assertThat(mapper.readValue("{\"amount\":10}", Holder.class).amount().code()).isEqualTo("AED");
        assertThat(mapper.readValue("{\"amount\":{\"value\":10}}", Holder.class).amount().code()).isEqualTo("AED");
    }

    @Test
    void refusesAnInvalidCodeTooManyDecimalsOrANonNumber() {
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":{\"code\":\"XYZ\",\"value\":1}}", Holder.class))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":{\"code\":\"INR\",\"value\":1.005}}", Holder.class))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":\"abc\"}", Holder.class)).isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"amount\":true}", Holder.class)).isInstanceOf(JsonMappingException.class);
    }

    @Test
    void positiveMoneyAcceptsNullAndPositiveOnly() {
        PositiveMoney.Validator validator = new PositiveMoney.Validator();
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid(Money.of(BigDecimal.ONE, "INR"), null)).isTrue();
        assertThat(validator.isValid(Money.of(BigDecimal.ZERO, "INR"), null)).isFalse();
        assertThat(validator.isValid(Money.of(new BigDecimal("-1"), "INR"), null)).isFalse();
    }
}
