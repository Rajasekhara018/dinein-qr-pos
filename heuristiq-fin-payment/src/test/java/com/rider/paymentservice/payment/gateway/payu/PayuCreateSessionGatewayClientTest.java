package com.heuristiq.paymentservice.payment.gateway.payu;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.config.PayuGatewayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.util.Sha512Hasher;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PayU's checkout "session" is entirely local — a set of SHA-512-hash-signed form fields, no
 * outbound HTTP call happens at this step (the browser itself POSTs the fields to PayU). This
 * test proves the hash-chain field order/formula is exactly what PayU's own verification expects.
 */
class PayuCreateSessionGatewayClientTest {

    private PayuGatewayProperties props;
    private PayuCreateSessionGatewayClient client;

    @BeforeEach
    void setUp() {
        props = new PayuGatewayProperties();
        props.setKey("gtKFFxTest");
        props.setSaltV1("test-salt-value");
        props.setSuccessUrl("https://rider.app/payu/success");
        props.setFailureUrl("https://rider.app/payu/failure");
        client = new PayuCreateSessionGatewayClient(props);
    }

    private Payment payment() {
        return Payment.builder()
                .internalPaymentId("pay-abc-123")
                .rideId("ride-1")
                .customerId("cust-1")
                .amount(new BigDecimal("199.5"))
                .currency("INR")
                .method(PaymentMethod.RAZORPAY)
                .status(PaymentStatus.INITIATED)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSession_returnsHashSignedHostedFormFields() {
        GatewaySessionResult result = client.createSession(payment());

        assertThat(client.getGatewayType()).isEqualTo(PaymentGatewayType.PAYU);
        assertThat(result.razorpayOrder()).isNull();
        assertThat(result.providerOrderId()).isNotBlank();

        Map<String, Object> session = result.gatewaySession();
        assertThat(session.get("action")).isEqualTo(props.getPaymentUrl());
        assertThat(session.get("method")).isEqualTo("post");

        Map<String, String> fields = (Map<String, String>) session.get("fields");
        assertThat(fields.get("key")).isEqualTo("gtKFFxTest");
        assertThat(fields.get("txnid")).isEqualTo(result.providerOrderId());
        assertThat(fields.get("amount")).isEqualTo("199.50");
        assertThat(fields.get("surl")).isEqualTo(props.getSuccessUrl());
        assertThat(fields.get("furl")).isEqualTo(props.getFailureUrl());

        // Independently recompute the exact hash-chain formula PayU verifies server-side, proving
        // field order/formula fidelity rather than just "some hash was set".
        String raw = fields.get("key") + "|" + fields.get("txnid") + "|" + fields.get("amount") + "|"
                + fields.get("productinfo") + "|" + fields.get("firstname") + "|" + fields.get("email") + "|"
                + fields.get("udf1") + "|" + "" + "|" + "" + "|" + "" + "|" + fields.get("udf5") + "||||||";
        String expectedHash = Sha512Hasher.hex(raw + props.getSaltV1());
        assertThat(fields.get("hash")).isEqualTo(expectedHash);
    }

    @Test
    void createSession_throwsWhenKeyOrSaltNotConfigured() {
        PayuGatewayProperties unconfigured = new PayuGatewayProperties();
        PayuCreateSessionGatewayClient unconfiguredClient = new PayuCreateSessionGatewayClient(unconfigured);

        assertThatThrownBy(() -> unconfiguredClient.createSession(payment()))
                .isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("not configured");
    }
}
