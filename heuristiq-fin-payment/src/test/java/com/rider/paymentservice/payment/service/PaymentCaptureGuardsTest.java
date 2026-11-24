package com.heuristiq.paymentservice.payment.service;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.client.NmsServiceClient;
import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.config.PaymentPollingProperties;
import com.heuristiq.paymentservice.config.RazorpayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.paymentservice.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.model.PaymentWebhookEvent;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.payment.repository.PaymentWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PAY-04 (amount and currency must match before a payment is captured, on every capture path) and
 * PAY-05 (a late failure never overwrites CAPTURED; provider_payment_id is never overwritten).
 */
class PaymentCaptureGuardsTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy_secret";

    private PaymentRepository paymentRepository;
    private RideServiceClient rideServiceClient;
    private PaymentGatewayClientResolver gatewayClientResolver;
    private StatusCheckGatewayClient statusCheckGatewayClient;
    private PaymentWebhookHandler webhookHandler;
    private PaymentStatusPollingService pollingService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        rideServiceClient = mock(RideServiceClient.class);
        NmsServiceClient nmsServiceClient = mock(NmsServiceClient.class);
        PaymentWebhookEventRepository webhookEventRepository = mock(PaymentWebhookEventRepository.class);
        gatewayClientResolver = mock(PaymentGatewayClientResolver.class);
        statusCheckGatewayClient = mock(StatusCheckGatewayClient.class);

        RazorpayProperties razorpayProperties = new RazorpayProperties();
        razorpayProperties.setWebhookSecret(WEBHOOK_SECRET);
        PaymentTransitionEffects effects = new PaymentTransitionEffects(
                paymentRepository, rideServiceClient, nmsServiceClient, org.mockito.Mockito.mock(com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService.class));
        GatewayAmountVerifier verifier = new GatewayAmountVerifier(new com.heuristiq.paymentservice.config.MoneyProperties());
        webhookHandler = new PaymentWebhookHandler(paymentRepository, webhookEventRepository,
                new RazorpayWebhookSignatureVerifier(razorpayProperties), effects, verifier, mock(WebhookAuditRecorder.class));
        PaymentPollingProperties polling = new PaymentPollingProperties();
        pollingService = new PaymentStatusPollingService(paymentRepository, gatewayClientResolver, polling, effects, verifier,
                new org.springframework.transaction.support.TransactionTemplate(mock(org.springframework.transaction.PlatformTransactionManager.class)));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, PaymentWebhookEvent> store = new HashMap<>();
        when(webhookEventRepository.findByProviderEventId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent event = inv.getArgument(0);
            store.put(event.getProviderEventId(), event);
            return event;
        });
    }

    private Payment payment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .internalPaymentId("pay-internal-1").rideId("ride-1").customerId("cust-1").driverId("driver-1")
                .amount(new BigDecimal("150.0")).currency("INR").method(PaymentMethod.RAZORPAY).gateway(PaymentGatewayType.RAZORPAY)
                .status(status).providerOrderId("order_abc").idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now().minusMinutes(10)).updatedAt(LocalDateTime.now().minusMinutes(10))
                .build();
        when(paymentRepository.findByProviderOrderId("order_abc")).thenReturn(Optional.of(payment));
        return payment;
    }

    private void deliver(String event, String paymentId, String extraFields) {
        String body = "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"order_abc\"" + extraFields + "}}}}";
        webhookHandler.handleWebhook(body, hmac(body), null);
    }

    // ── PAY-04: amount and currency ─────────────────────────────────────────

    @Test
    void webhookCaptured_withWrongAmount_isNotCaptured_andIsFlagged() {
        Payment payment = payment(PaymentStatus.INITIATED);

        deliver("payment.captured", "pay_1", ",\"amount\":100,\"currency\":\"INR\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(payment.getFailureReason()).startsWith("AMOUNT_MISMATCH").contains("15000").contains("100");
        assertThat(payment.getProviderPaymentId()).isNull();
        verify(rideServiceClient, never()).notifyPaymentResult(anyString(), anyString(), anyString(), any());
    }

    @Test
    void webhookCaptured_withWrongCurrency_isNotCaptured() {
        Payment payment = payment(PaymentStatus.INITIATED);

        deliver("payment.captured", "pay_1", ",\"amount\":15000,\"currency\":\"USD\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    @Test
    void webhookCaptured_withNoAmount_failsClosed() {
        Payment payment = payment(PaymentStatus.INITIATED);

        deliver("payment.captured", "pay_1", "");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    @Test
    void webhookCaptured_withMatchingAmount_captures() {
        Payment payment = payment(PaymentStatus.INITIATED);

        deliver("payment.captured", "pay_1", ",\"amount\":15000,\"currency\":\"INR\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_1");
    }

    @Test
    void poller_gatewayReportsSuccessWithWrongAmount_doesNotCapture() {
        Payment payment = payment(PaymentStatus.INITIATED);
        when(paymentRepository.findPollCandidatesAfter(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_abc", "pay_1", "SUCCESS", "paid", new BigDecimal("1.0"), "INR"));

        pollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
        assertThat(payment.getProviderPaymentId()).isNull();
    }

    @Test
    void poller_gatewayReportsSuccessWithoutAnAmount_doesNotCapture() {
        Payment payment = payment(PaymentStatus.INITIATED);
        when(paymentRepository.findPollCandidatesAfter(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_abc", "pay_1", "SUCCESS", "paid", null, "INR"));

        pollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    @Test
    void poller_wrongCurrency_doesNotCapture() {
        Payment payment = payment(PaymentStatus.INITIATED);
        when(paymentRepository.findPollCandidatesAfter(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_abc", "pay_1", "SUCCESS", "paid", new BigDecimal("150.0"), "USD"));

        pollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AMOUNT_MISMATCH);
    }

    // ── PAY-05: state guards ─────────────────────────────────────────────────

    @Test
    void lateWebhookPaymentFailed_neverOverwritesCaptured() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        payment.setProviderPaymentId("pay_1");

        deliver("payment.failed", "pay_1", ",\"error_description\":\"Card declined\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getFailureReason()).isNull();
        verify(rideServiceClient, never()).notifyPaymentResult(anyString(), anyString(), anyString(), any());
    }

    @Test
    void lateWebhookPaymentFailed_fromAnotherAttempt_isIgnoredAndKeepsRecordedPaymentId() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        payment.setProviderPaymentId("pay_good");

        deliver("payment.failed", "pay_other_attempt", ",\"error_description\":\"Card declined\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_good");
    }

    @Test
    void capturedEventWithADifferentPaymentId_isIgnored_providerPaymentIdNotOverwritten() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        payment.setProviderPaymentId("pay_good");

        deliver("payment.captured", "pay_second", ",\"amount\":15000,\"currency\":\"INR\"");

        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_good");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void failedAttemptThenSuccessfulRetryOnTheSameOrder_isStillCaptured() {
        Payment payment = payment(PaymentStatus.INITIATED);

        deliver("payment.failed", "pay_attempt1", ",\"error_description\":\"Declined\"");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getProviderPaymentId()).isNull();

        deliver("payment.captured", "pay_attempt2", ",\"amount\":15000,\"currency\":\"INR\"");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_attempt2");
    }

    @Test
    void poller_gatewayReportsFailed_neverOverwritesCapturedPayment() {
        Payment payment = payment(PaymentStatus.CAPTURED);
        when(paymentRepository.findById("pay-internal-1")).thenReturn(Optional.of(payment));
        payment.setGateway(PaymentGatewayType.RAZORPAY);

        // reconcile() only polls INITIATED/AUTHORIZED, so drive the poll loop directly with a CAPTURED candidate.
        when(paymentRepository.findPollCandidatesAfter(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_abc", null, "FAILED", "failed", new BigDecimal("150.0"), "INR"));

        pollingService.pollPendingGatewayPayments();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        verify(rideServiceClient, never()).notifyPaymentResult(anyString(), anyString(), anyString(), any());
    }

    @Test
    void poller_successWithDifferentProviderPaymentId_isIgnored() {
        Payment payment = payment(PaymentStatus.AUTHORIZED);
        payment.setProviderPaymentId("pay_recorded");
        when(paymentRepository.findPollCandidatesAfter(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(payment));
        when(paymentRepository.findByIdForUpdate(payment.getInternalPaymentId())).thenReturn(Optional.of(payment));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.RAZORPAY)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_abc", "pay_other", "SUCCESS", "paid", new BigDecimal("150.0"), "INR"));

        pollingService.pollPendingGatewayPayments();

        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_recorded");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
