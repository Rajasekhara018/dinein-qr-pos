package com.heuristq.dinein.payment.gateway.payu;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PayuHashTest {

    private static final String KEY = "gtKFFx";
    private static final String SALT = "salt123";

    @Test
    void requestHashFollowsPayuFormula() {
        Map<String, String> f = Map.of("txnid", "DI1X", "amount", "105.00", "productinfo", "Order 1",
                "firstname", "Guest", "email", "a@b.c", "udf1", "1");
        String expected = PayuHash.sha512(KEY + "|DI1X|105.00|Order 1|Guest|a@b.c|1|||||||||||" + SALT);

        assertThat(PayuHash.request(KEY, SALT, f)).isEqualTo(expected);
    }

    @Test
    void verifiesResponseHashAndDetectsTampering() {
        Map<String, String> f = new HashMap<>(Map.of("status", "success", "txnid", "DI1X", "amount", "105.00",
                "productinfo", "Order 1", "firstname", "Guest", "email", "a@b.c", "udf1", "1"));
        f.put("hash", PayuHash.sha512(SALT + "|success||||||||||1|a@b.c|Guest|Order 1|105.00|DI1X|" + KEY));

        assertThat(PayuHash.verifyResponse(KEY, SALT, f)).isTrue();

        f.put("amount", "1.00");
        assertThat(PayuHash.verifyResponse(KEY, SALT, f)).isFalse();
    }

    @Test
    void responseHashIncludesAdditionalChargesWhenPresent() {
        Map<String, String> f = new HashMap<>(Map.of("status", "success", "txnid", "T", "amount", "10.00",
                "productinfo", "P", "firstname", "F", "email", "e", "additionalCharges", "2.00"));
        f.put("hash", PayuHash.sha512("2.00|" + SALT + "|success|||||||||||e|F|P|10.00|T|" + KEY));

        assertThat(PayuHash.verifyResponse(KEY, SALT, f)).isTrue();
    }
}
