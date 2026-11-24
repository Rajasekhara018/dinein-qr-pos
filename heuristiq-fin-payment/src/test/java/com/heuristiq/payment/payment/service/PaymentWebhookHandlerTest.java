package com.heuristiq.payment.payment.service;

import java.math.BigDecimal;
import com.heuristiq.payment.client.NmsServiceClient;
import com.heuristiq.payment.client.RideServiceClient;
import com.heuristiq.payment.client.dto.NotificationRequest;
import com.heuristiq.payment.config.RazorpayProperties;
import com.heuristiq.payment.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.payment.gatewayaudit.WebhookObservation;
import com.heuristiq.payment.gatewayaudit.model.WebhookOutcome;
import com.heuristiq.payment.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.model.PaymentWebhookEvent;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PaymentWebhookHandler — the Razorpay ride-payment webhook, extracted out of PaymentService
 * (see that class's javadoc). Covers HLD §18/§23.3 idempotency (the same payload delivered twice
 * applies the transition and its side effects exactly once), signature rejection, and that an
 * NMS outage during a payment.failed transition degrades non-fatally rather than failing the
 * webhook handler.
 */
class PaymentWebhookHandlerTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy_secret";

    private PaymentRepository paymentRepository;
    private PaymentWebhookEventRepository webhookEventRepository;
    private RideServiceClient rideServiceClient;
    private NmsServiceClient nmsServiceClient;
    private PaymentWebhookHandler webhookHandler;
    private WebhookAuditRecorder webhookAudit;
    private final com.heuristiq.payment.salesettlement.application.SaleSettlementService saleSettlement =
            mock(com.heuristiq.payment.salesettlement.application.SaleSettlementService.class);

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        webhookEventRepository = mock(PaymentWebhookEventRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        nmsServiceClient = mock(NmsServiceClient.class);

        RazorpayProperties razorpayProperties = new RazorpayProperties();
        razorpayProperties.setWebhookSecret(WEBHOOK_SECRET);
        RazorpayWebhookSignatureVerifier webhookSignatureVerifier = new RazorpayWebhookSignatureVerifier(razorpayProperties);
        PaymentTransitionEffects transitionEffects = new PaymentTransitionEffects(
                paymentRepository, rideServiceClient, nmsServiceClient, saleSettlement);

        webhookAudit = mock(WebhookAuditRecorder.class);
        webhookHandler = new PaymentWebhookHandler(
                paymentRepository, webhookEventRepository, webhookSignatureVerifier, transitionEffects, new GatewayAmountVerifier(new com.heuristiq.payment.config.MoneyProperties()), webhookAudit);

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Fake in-memory backing store for the dedupe ledger, driven purely through the two
        // repository methods PaymentWebhookHandler actually calls — proves the real dedupe logic,
        // not a mocked short-circuit.
        Map<String, PaymentWebhookEvent> store = new HashMap<>();
        when(webhookEventRepository.findByProviderEventId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent event = inv.getArgument(0);
            store.put(event.getProviderEventId(), event);
            return event;
        });
    }

    @Test
    void sameWebhookPayloadProcessedTwice_onlyAppliesStateTransitionAndSideEffectsOnce() {
        Payment payment = Payment.builder()
                .internalPaymentId("pay-internal-1")
                .rideId("ride-1")
                .customerId("cust-1")
                .driverId("driver-1")
                .amount(new BigDecimal("150.0"))
                .currency("INR")
                .method(PaymentMethod.RAZORPAY)
                .status(PaymentStatus.INITIATED)
                .providerOrderId("order_abc123")
                .idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenAnswer(inv -> Optional.of(payment));

        String rawBody = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_xyz789","order_id":"order_abc123","amount":15000,"currency":"INR"}}}}""";
        String signature = hmacSha256Hex(rawBody, WEBHOOK_SECRET);

        // First delivery — applies the transition.
        webhookHandler.handleWebhook(rawBody, signature, null);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_xyz789");

        // Second delivery of the byte-identical payload — must be a no-op.
        webhookHandler.handleWebhook(rawBody, signature, null);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(rideServiceClient, times(1)).notifyPaymentResult("ride-1", "COMPLETED", "pay-internal-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("150.0"), "INR"));
        verify(webhookEventRepository, times(1)).save(any(PaymentWebhookEvent.class));
    }

    @Test
    void rejectsWebhookWithInvalidSignature() {
        String rawBody = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_xyz789","order_id":"order_abc123","amount":15000,"currency":"INR"}}}}""";

        org.junit.jupiter.api.Assertions.assertThrows(
                com.heuristiq.payment.shared.exception.PaymentServiceException.class,
                () -> webhookHandler.handleWebhook(rawBody, "not-the-right-signature", null));
    }

    @Test
    void webhookPaymentFailed_nmsUnavailable_doesNotFailWebhookHandling() {
        Payment payment = Payment.builder()
                .internalPaymentId("pay-internal-2")
                .rideId("ride-2")
                .customerId("cust-2")
                .driverId("driver-2")
                .amount(new BigDecimal("200.0"))
                .currency("INR")
                .method(PaymentMethod.RAZORPAY)
                .status(PaymentStatus.INITIATED)
                .providerOrderId("order_xyz")
                .idempotencyKey("idem-2")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(paymentRepository.findByProviderOrderId("order_xyz")).thenReturn(Optional.of(payment));
        doThrow(new RuntimeException("NMS service unreachable"))
                .when(nmsServiceClient).notify(any(NotificationRequest.class));

        String rawBody = """
                {"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_fail1","order_id":"order_xyz","error_description":"Card declined"}}}}""";
        String signature = hmacSha256Hex(rawBody, WEBHOOK_SECRET);

        assertDoesNotThrow(() -> webhookHandler.handleWebhook(rawBody, signature, null));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(rideServiceClient).notifyPaymentResult("ride-2", "FAILED", "pay-internal-2", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("200.0"), "INR"));
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(nmsServiceClient).notify(captor.capture());
        assertEquals("PAYMENT_FAILED", captor.getValue().getEvent());
        assertEquals("cust-2", captor.getValue().getUserIds().get(0));
    }

    private static final String CAPTURED_BODY = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_xyz789","order_id":"order_abc123","amount":15000,"currency":"INR"}}}}""";

    private Payment capturablePayment() {
        Payment payment = Payment.builder()
                .internalPaymentId("pay-internal-1").rideId("ride-1").customerId("cust-1").driverId("driver-1")
                .amount(new BigDecimal("150.0")).currency("INR").method(PaymentMethod.RAZORPAY).status(PaymentStatus.INITIATED)
                .providerOrderId("order_abc123").idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenAnswer(inv -> Optional.of(payment));
        return payment;
    }

    @Test
    void invalidSignature_isRecordedAsRejectedWithoutPayloadOrSignature() {
        org.junit.jupiter.api.Assertions.assertThrows(
                com.heuristiq.payment.shared.exception.PaymentServiceException.class,
                () -> webhookHandler.handleWebhook(CAPTURED_BODY, "forged-signature-value", "evt-1"));

        ArgumentCaptor<WebhookObservation> captor = ArgumentCaptor.forClass(WebhookObservation.class);
        verify(webhookAudit).recordUndelivered(captor.capture());
        WebhookObservation o = captor.getValue();
        assertThat(o.outcome()).isEqualTo(WebhookOutcome.REJECTED);
        assertThat(o.signatureValid()).isFalse();
        assertThat(o.providerEventId()).isEqualTo("evt-1");
        assertThat(o.provider()).isEqualTo("RAZORPAY");
        assertThat(o.toString()).doesNotContain("forged-signature-value").doesNotContain("pay_xyz789");
        verify(webhookEventRepository, times(0)).save(any(PaymentWebhookEvent.class));
    }

    @Test
    void firstDelivery_isRecordedAsProcessed_andSecondAsDuplicate() {
        capturablePayment();
        String signature = hmacSha256Hex(CAPTURED_BODY, WEBHOOK_SECRET);

        webhookHandler.handleWebhook(CAPTURED_BODY, signature, "evt-9");
        webhookHandler.handleWebhook(CAPTURED_BODY, signature, "evt-9");

        ArgumentCaptor<PaymentWebhookEvent> row = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(webhookEventRepository, times(1)).save(row.capture());
        assertThat(row.getValue().getProvider()).isEqualTo("RAZORPAY");
        assertThat(row.getValue().getSignatureValid()).isTrue();
        assertThat(row.getValue().getOutcome()).isEqualTo("PROCESSED");
        assertThat(row.getValue().getProviderEventId()).isEqualTo("evt-9");
        assertThat(row.getValue().getPaymentId()).isEqualTo("pay-internal-1");

        ArgumentCaptor<WebhookObservation> processed = ArgumentCaptor.forClass(WebhookObservation.class);
        verify(webhookAudit).publishProcessed(org.mockito.ArgumentMatchers.eq(row.getValue().getId()), processed.capture());
        assertThat(processed.getValue().outcome()).isEqualTo(WebhookOutcome.PROCESSED);

        ArgumentCaptor<WebhookObservation> dup = ArgumentCaptor.forClass(WebhookObservation.class);
        verify(webhookAudit).recordUndelivered(dup.capture());
        assertThat(dup.getValue().outcome()).isEqualTo(WebhookOutcome.DUPLICATE);
        assertThat(dup.getValue().signatureValid()).isTrue();
        assertThat(dup.getValue().paymentId()).isEqualTo("pay-internal-1");
    }

    @Test
    void webhookForUnknownOrder_isRecordedAsIgnored() {
        when(paymentRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.empty());
        when(paymentRepository.findByProviderPaymentId(anyString())).thenReturn(Optional.empty());

        webhookHandler.handleWebhook(CAPTURED_BODY, hmacSha256Hex(CAPTURED_BODY, WEBHOOK_SECRET), "evt-10");

        ArgumentCaptor<PaymentWebhookEvent> row = ArgumentCaptor.forClass(PaymentWebhookEvent.class);
        verify(webhookEventRepository).save(row.capture());
        assertThat(row.getValue().getOutcome()).isEqualTo("IGNORED");
        assertThat(row.getValue().getPaymentId()).isNull();
    }

    private static String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
