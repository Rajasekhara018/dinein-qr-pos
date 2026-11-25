package com.heuristiq.identity.pos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The per-country rules of the POS: currency, locale, financial year, postal codes. */
class PosCountryTest {

    @Test
    void everySupportedCountryHasItsCurrency() {
        assertThat(PosCountry.IN.currency()).isEqualTo("INR");
        assertThat(PosCountry.DE.currency()).isEqualTo("EUR");
        assertThat(PosCountry.GB.currency()).isEqualTo("GBP");
        assertThat(PosCountry.FR.currency()).isEqualTo("EUR");
        assertThat(PosCountry.US.currency()).isEqualTo("USD");
        assertThat(PosCountry.AU.currency()).isEqualTo("AUD");
        assertThat(PosCountry.IN.defaultLocale()).isEqualTo("en-IN");
        assertThat(PosCountry.AU.defaultFinancialYearStartMonth()).isEqualTo(7);
        assertThat(PosCountry.GB.defaultFinancialYearStartMonth()).isEqualTo(4);
        assertThat(PosCountry.US.regionRequired()).isTrue();
        assertThat(PosCountry.DE.regionRequired()).isFalse();
    }

    @Test
    void ofIsCaseInsensitiveAndRefusesUnknown() {
        assertThat(PosCountry.of("de")).contains(PosCountry.DE);
        assertThat(PosCountry.of("IT")).isEmpty();
        assertThat(PosCountry.of(null)).isEmpty();
    }

    @Test
    void postalCodes() {
        assertThat(PosCountry.GB.acceptsPostalCode("SW1A 1AA")).isTrue();
        assertThat(PosCountry.GB.acceptsPostalCode("M1 1AE")).isTrue();
        assertThat(PosCountry.US.acceptsPostalCode("94105-1234")).isTrue();
        assertThat(PosCountry.IN.acceptsPostalCode("012345")).isFalse();
        assertThat(PosCountry.AU.acceptsPostalCode("20000")).isFalse();
        assertThat(PosCountry.DE.acceptsPostalCode(" 10115 ")).isTrue();
        assertThat(PosCountry.DE.acceptsPostalCode(null)).isFalse();
    }
}
