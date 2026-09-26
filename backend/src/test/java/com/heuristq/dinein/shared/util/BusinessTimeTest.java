package com.heuristq.dinein.shared.util;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeTest {

    @Test
    void sameDayWindowIsHalfOpen() {
        LocalTime open = LocalTime.of(11, 0);
        LocalTime close = LocalTime.of(23, 0);

        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(11, 0))).isTrue();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(22, 59))).isTrue();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(23, 0))).isFalse();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(10, 59))).isFalse();
    }

    @Test
    void overnightWindowWrapsPastMidnight() {
        LocalTime open = LocalTime.of(18, 0);
        LocalTime close = LocalTime.of(2, 0);

        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(23, 30))).isTrue();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(1, 59))).isTrue();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(2, 0))).isFalse();
        assertThat(BusinessTime.isWithin(open, close, LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void missingOrEqualBoundsMeanAlwaysOpen() {
        assertThat(BusinessTime.isWithin(null, null, LocalTime.NOON)).isTrue();
        assertThat(BusinessTime.isWithin(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, LocalTime.NOON)).isTrue();
    }

    @Test
    void moneyConvertsToPaiseExactly() {
        assertThat(Money.toPaise(new java.math.BigDecimal("586.60"))).isEqualTo(58660L);
        assertThat(Money.fromPaise(58660L)).isEqualByComparingTo("586.60");
    }
}
