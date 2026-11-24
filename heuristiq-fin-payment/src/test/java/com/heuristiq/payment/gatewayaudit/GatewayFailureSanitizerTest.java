package com.heuristiq.payment.gatewayaudit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayFailureSanitizerTest {

    @Test
    void nullMessage_givesJustTheClassName() {
        assertThat(GatewayFailureSanitizer.reasonFor(new IllegalStateException(), 200)).isEqualTo("IllegalStateException");
    }

    @Test
    void longMessageIsBounded_andControlCharactersRemoved() {
        String reason = GatewayFailureSanitizer.reasonFor(new RuntimeException("a\nb\t" + "x".repeat(500)), 50);
        assertThat(reason).hasSize(50).doesNotContain("\n").doesNotContain("\t");
    }

    @Test
    void jwtsAndEmbeddedBodiesAreRemoved() {
        String cleaned = GatewayFailureSanitizer.clean(
                "bad token eyJhbGciOi.eyJzdWIiOiIx.sig response <html>secret page</html>", 200);
        assertThat(cleaned).doesNotContain("eyJhbGciOi").doesNotContain("secret page");
    }
}
