package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KioskVersionGateTest {

    private static MockHttpServletRequest requestWithVersion(String version) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (version != null) {
            request.addHeader(KioskVersionGate.VERSION_HEADER, version);
        }
        return request;
    }

    @Test
    void comparesNumericallyNotAlphabetically() {
        assertThat(KioskVersionGate.isOlder("1.2.9", "1.2.10")).isTrue();
        assertThat(KioskVersionGate.isOlder("1.2.10", "1.2.9")).isFalse();
        assertThat(KioskVersionGate.isOlder("1.0", "1.0.0")).isFalse();
        assertThat(KioskVersionGate.isOlder("1.0.0", "1.0.0")).isFalse();
        assertThat(KioskVersionGate.isOlder("0.9.9", "1.0.0")).isTrue();
    }

    @Test
    void buildSuffixIsIgnoredAndGarbageIsNotBlocked() {
        assertThat(KioskVersionGate.isOlder("1.0.0+7", "1.0.0")).isFalse();
        assertThat(KioskVersionGate.isOlder("dev", "1.0.0")).isFalse();
    }

    @Test
    void oldAppGets426() {
        KioskVersionGate gate = new KioskVersionGate("1.2.0");
        assertThatThrownBy(() -> gate.preHandle(requestWithVersion("1.1.9"), new MockHttpServletResponse(), new Object()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus().value()).isEqualTo(426);
                    assertThat(e.getCode()).isEqualTo("APP_UPDATE_REQUIRED");
                });
    }

    @Test
    void currentAppUnknownClientAndDisabledGateAreAllowed() {
        KioskVersionGate gate = new KioskVersionGate("1.2.0");
        assertThat(gate.preHandle(requestWithVersion("1.2.0"), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(gate.preHandle(requestWithVersion(null), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(new KioskVersionGate("")
                .preHandle(requestWithVersion("0.0.1"), new MockHttpServletResponse(), new Object())).isTrue();
    }
}
