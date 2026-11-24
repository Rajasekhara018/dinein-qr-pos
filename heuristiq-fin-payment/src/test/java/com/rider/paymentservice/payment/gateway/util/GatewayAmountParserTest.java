package com.heuristiq.paymentservice.payment.gateway.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayAmountParserTest {

    @Test
    void readsARupeeAmountExactlyWithTwoDecimals() {
        assertThat(GatewayAmountParser.rupees("150")).isEqualByComparingTo("150.00").hasScaleOf(2);
        assertThat(GatewayAmountParser.rupees(" 99.995 ")).isEqualByComparingTo("100.00");
        assertThat(GatewayAmountParser.rupees("0.1")).isEqualByComparingTo("0.10");
    }

    @Test
    void readsAPaiseAmountAsRupees() {
        assertThat(GatewayAmountParser.fromSubunits("15000")).isEqualByComparingTo("150.00").hasScaleOf(2);
        assertThat(GatewayAmountParser.fromSubunits("1")).isEqualByComparingTo("0.01");
    }

    @Test
    void aMissingOrUnreadableAmountIsNull() {
        assertThat(GatewayAmountParser.rupees(null)).isNull();
        assertThat(GatewayAmountParser.rupees("  ")).isNull();
        assertThat(GatewayAmountParser.rupees("abc")).isNull();
        assertThat(GatewayAmountParser.fromSubunits(null)).isNull();
        assertThat(GatewayAmountParser.fromSubunits("12x")).isNull();
    }
}
