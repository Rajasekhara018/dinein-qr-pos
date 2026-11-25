package com.heuristiq.payment.payment.gateway.razorpay;

import java.math.BigDecimal;
import com.razorpay.Order;
import com.razorpay.OrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.heuristiq.payment.config.RazorpayProperties;
import com.heuristiq.payment.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mocks the outbound HTTP layer (the razorpay-java SDK's OrderClient, which does the actual
 * network call) — same pattern PaymentServiceRefundTest uses for RazorpayRefundClient.
 */
class RazorpayCreateSessionGatewayClientTest {

    private OrderClient orderClient;
    private RazorpayCreateSessionGatewayClient client;

    @BeforeEach
    void setUp() {
        RazorpayClient razorpayClient = mock(RazorpayClient.class);
        orderClient = mock(OrderClient.class);
        razorpayClient.orders = orderClient;

        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_dummy");

        client = new RazorpayCreateSessionGatewayClient(razorpayClient, props);
    }

    private Payment payment() {
        return Payment.builder()
                .internalPaymentId("pay-1")
                .rideId("ride-1")
                .customerId("cust-1")
                .amount(new BigDecimal("250.0"))
                .currency("INR")
                .method(PaymentMethod.RAZORPAY)
                .status(PaymentStatus.INITIATED)
                .build();
    }

    @Test
    void createSession_returnsRazorpayOrderDetails() throws RazorpayException {
        when(orderClient.create(any(JSONObject.class)))
                .thenReturn(new Order(new JSONObject().put("id", "order_test123")));

        GatewaySessionResult result = client.createSession(payment());

        assertThat(result.providerOrderId()).isEqualTo("order_test123");
        assertThat(result.razorpayOrder()).isNotNull();
        assertThat(result.razorpayOrder().orderId()).isEqualTo("order_test123");
        assertThat(result.razorpayOrder().amountInPaise()).isEqualTo(25000L);
        assertThat(result.razorpayOrder().keyId()).isEqualTo("rzp_test_dummy");
        assertThat(result.gatewaySession()).isNull();
        assertThat(client.getGatewayType()).isEqualTo(PaymentGatewayType.RAZORPAY);
    }

    @Test
    void createSession_propagatesGatewayFailure_asPaymentServiceException() throws RazorpayException {
        when(orderClient.create(any(JSONObject.class))).thenThrow(new RazorpayException("network error"));

        assertThatThrownBy(() -> client.createSession(payment()))
                .isInstanceOf(PaymentServiceException.class);
    }
}
