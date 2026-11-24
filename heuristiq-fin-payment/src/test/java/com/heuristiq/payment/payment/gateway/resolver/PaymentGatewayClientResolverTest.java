package com.heuristiq.payment.payment.gateway.resolver;

import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.payment.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the resolver's core job: pick the right adapter per {@link PaymentGatewayType} for each
 * of the 3 ports, and fail with a clear error (not an NPE/silent wrong-gateway-call) when a
 * gateway has no adapter registered for a given port — e.g. TOUCAN/PINELABS have no
 * {@link RefundGatewayClient} bean in this pass (no refund adapter exists for either in the
 * reference design this was ported from).
 */
class PaymentGatewayClientResolverTest {

    private CreateSessionGatewayClient createClientFor(PaymentGatewayType type) {
        CreateSessionGatewayClient client = mock(CreateSessionGatewayClient.class);
        when(client.getGatewayType()).thenReturn(type);
        return client;
    }

    private RefundGatewayClient refundClientFor(PaymentGatewayType type) {
        RefundGatewayClient client = mock(RefundGatewayClient.class);
        when(client.getGatewayType()).thenReturn(type);
        return client;
    }

    private StatusCheckGatewayClient statusClientFor(PaymentGatewayType type) {
        StatusCheckGatewayClient client = mock(StatusCheckGatewayClient.class);
        when(client.getGatewayType()).thenReturn(type);
        return client;
    }

    @Test
    void resolvesCreateSessionClient_forEachRegisteredGateway() {
        CreateSessionGatewayClient razorpay = createClientFor(PaymentGatewayType.RAZORPAY);
        CreateSessionGatewayClient payu = createClientFor(PaymentGatewayType.PAYU);
        CreateSessionGatewayClient toucan = createClientFor(PaymentGatewayType.TOUCAN);
        CreateSessionGatewayClient pinelabs = createClientFor(PaymentGatewayType.PINELABS);

        PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(
                List.of(razorpay, payu, toucan, pinelabs), List.of(), List.of());

        assertThat(resolver.resolveCreateSession(PaymentGatewayType.RAZORPAY)).isSameAs(razorpay);
        assertThat(resolver.resolveCreateSession(PaymentGatewayType.PAYU)).isSameAs(payu);
        assertThat(resolver.resolveCreateSession(PaymentGatewayType.TOUCAN)).isSameAs(toucan);
        assertThat(resolver.resolveCreateSession(PaymentGatewayType.PINELABS)).isSameAs(pinelabs);
    }

    @Test
    void resolveCreateSession_throwsClearError_whenGatewayNotRegistered() {
        PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(List.of(), List.of(), List.of());

        assertThatThrownBy(() -> resolver.resolveCreateSession(PaymentGatewayType.PAYU))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("PAYU");
    }

    @Test
    void resolvesRefundClient_onlyForGatewaysThatHaveOne() {
        RefundGatewayClient razorpay = refundClientFor(PaymentGatewayType.RAZORPAY);
        RefundGatewayClient payu = refundClientFor(PaymentGatewayType.PAYU);

        PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(
                List.of(), List.of(razorpay, payu), List.of());

        assertThat(resolver.resolveRefund(PaymentGatewayType.RAZORPAY)).isSameAs(razorpay);
        assertThat(resolver.resolveRefund(PaymentGatewayType.PAYU)).isSameAs(payu);
        assertThatThrownBy(() -> resolver.resolveRefund(PaymentGatewayType.TOUCAN))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("TOUCAN");
        assertThatThrownBy(() -> resolver.resolveRefund(PaymentGatewayType.PINELABS))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("PINELABS");
    }

    @Test
    void resolvesStatusCheckClient_forEachRegisteredGateway() {
        StatusCheckGatewayClient toucan = statusClientFor(PaymentGatewayType.TOUCAN);
        PaymentGatewayClientResolver resolver = new PaymentGatewayClientResolver(
                List.of(), List.of(), List.of(toucan));

        assertThat(resolver.resolveStatusCheck(PaymentGatewayType.TOUCAN)).isSameAs(toucan);
        assertThatThrownBy(() -> resolver.resolveStatusCheck(PaymentGatewayType.PINELABS))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("PINELABS");
    }
}
