package com.heuristq.dinein.payment.infrastructure.gateway;

import com.heuristq.dinein.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentGatewayRegistryTest {

    private static PaymentGateway gateway(String code) {
        PaymentGateway g = mock(PaymentGateway.class);
        when(g.code()).thenReturn(code);
        when(g.isConfigured()).thenReturn(true);
        return g;
    }

    @Test
    void offlineNeverResolvesToAGateway() {
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(gateway("RAZORPAY")),
                new PaymentProperties("RAZORPAY"));

        assertThat(registry.get("razorpay").code()).isEqualTo("RAZORPAY");
        for (String code : List.of("OFFLINE", "offline", " Offline ")) {
            assertThatThrownBy(() -> registry.get(code))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo("UNKNOWN_PROVIDER");
        }
    }

    @Test
    void offlineCannotBeTheActiveProvider() {
        assertThatThrownBy(() -> new PaymentGatewayRegistry(List.of(gateway("RAZORPAY")), new PaymentProperties("OFFLINE")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noGatewayMayClaimTheOfflineCode() {
        assertThatThrownBy(() -> new PaymentGatewayRegistry(List.of(gateway("RAZORPAY"), gateway("OFFLINE")),
                new PaymentProperties("RAZORPAY")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved");
    }
}
