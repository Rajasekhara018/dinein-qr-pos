package com.heuristiq.payment.wallet.service;

import com.heuristiq.payment.config.WalletServiceProperties;
import com.heuristiq.payment.wallet.port.WalletCreditClient;
import com.heuristiq.payment.wallet.port.WalletCreditCommand;
import com.heuristiq.payment.config.PaymentGatewayRoutingProperties;
import com.heuristiq.payment.config.PaymentPollingProperties;
import com.heuristiq.payment.config.RazorpayProperties;
import com.heuristiq.payment.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.payment.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.payment.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.payment.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.payment.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.payment.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentWebhookEvent;
import com.heuristiq.payment.payment.repository.PaymentWebhookEventRepository;
import com.heuristiq.payment.payment.service.GatewayAmountVerifier;
import com.heuristiq.payment.shared.exception.ConflictException;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.wallet.dto.CreateWalletTopupRequest;
import com.heuristiq.payment.wallet.model.WalletTopupSession;
import com.heuristiq.payment.wallet.model.WalletTopupStatus;
import com.heuristiq.payment.wallet.repository.WalletTopupSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

/** Wallet top-up capture guards (PAY-04, PAY-05) and the create-session restructuring (PAY-15). */
class WalletTopupGuardsTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy_secret";

    private WalletTopupSessionRepository sessionRepository;
    private WalletCreditClient walletCreditClient;
    private PaymentGatewayClientResolver resolver;
    private CreateSessionGatewayClient createClient;
    private StatusCheckGatewayClient statusClient;
    private WalletTopupService service;
    private final List<WalletTopupStatus> savedStatuses = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        var caller = new com.heuristiq.payment.security.AuthPrincipal("cust-1", "CUSTOMER", false, "org-a");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(caller, null, List.of()));
        sessionRepository = mock(WalletTopupSessionRepository.class);
        PaymentWebhookEventRepository events = mock(PaymentWebhookEventRepository.class);
        walletCreditClient = mock(WalletCreditClient.class);
        when(walletCreditClient.credit(any(WalletCreditCommand.class))).thenReturn("journal-1");
        resolver = mock(PaymentGatewayClientResolver.class);
        createClient = mock(CreateSessionGatewayClient.class);
        statusClient = mock(StatusCheckGatewayClient.class);
        RazorpayProperties razorpay = new RazorpayProperties();
        razorpay.setWebhookSecret(WEBHOOK_SECRET);
        PaymentPollingProperties polling = new PaymentPollingProperties();

        service = new WalletTopupService(sessionRepository, events,
                new WalletTopupCreditService(sessionRepository, walletCreditClient, new WalletServiceProperties(),
                        java.time.Clock.systemUTC()), resolver,
                new PaymentGatewayRoutingProperties(), polling, new RazorpayWebhookSignatureVerifier(razorpay),
                new GatewayAmountVerifier(new com.heuristiq.payment.config.MoneyProperties()), mock(WebhookAuditRecorder.class));

        when(sessionRepository.save(any(WalletTopupSession.class))).thenAnswer(inv -> {
            WalletTopupSession s = inv.getArgument(0);
            savedStatuses.add(s.getStatus());
            return s;
        });
        Map<String, PaymentWebhookEvent> store = new HashMap<>();
        when(events.findByProviderEventId(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(events.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent e = inv.getArgument(0);
            store.put(e.getProviderEventId(), e);
            return e;
        });
    }

    @org.junit.jupiter.api.AfterEach
    void clearCaller() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    private WalletTopupSession session(WalletTopupStatus status) {
        WalletTopupSession s = WalletTopupSession.builder().tenantId("org-a")
                .id("topup-1").customerId("cust-1").amount(new BigDecimal("500.0")).currency("INR").gateway(PaymentGatewayType.RAZORPAY)
                .status(status).providerOrderId("order_1").idempotencyKey("k")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(sessionRepository.findByProviderOrderId("order_1")).thenReturn(Optional.of(s));
        when(sessionRepository.findById("topup-1")).thenReturn(Optional.of(s));
        return s;
    }

    private void deliver(String event, String paymentId, String extra) {
        String body = "{\"event\":\"" + event + "\",\"payload\":{\"payment\":{\"entity\":{\"id\":\"" + paymentId
                + "\",\"order_id\":\"order_1\"" + extra + "}}}}";
        service.handleTopupWebhook(body, hmac(body), null);
    }

    @Test
    void webhookCapture_withWrongAmount_creditsNothing_andFlagsTheSession() {
        WalletTopupSession s = session(WalletTopupStatus.INITIATED);

        deliver("payment.captured", "pay_1", ",\"amount\":100,\"currency\":\"INR\"");

        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.AMOUNT_MISMATCH);
        verify(walletCreditClient, never()).credit(any());
    }

    @Test
    void webhookCapture_withoutAmount_failsClosed() {
        WalletTopupSession s = session(WalletTopupStatus.INITIATED);

        deliver("payment.captured", "pay_1", "");

        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.AMOUNT_MISMATCH);
        verify(walletCreditClient, never()).credit(any());
    }

    @Test
    void pollerSuccess_withWrongAmount_creditsNothing() {
        WalletTopupSession s = session(WalletTopupStatus.INITIATED);
        s.setGateway(PaymentGatewayType.PAYU);
        when(sessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(s));
        when(resolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusClient);
        when(statusClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "o", "p", "SUCCESS", "captured", new BigDecimal("5.0"), "INR"));

        service.pollPendingTopups();

        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.AMOUNT_MISMATCH);
        verify(walletCreditClient, never()).credit(any());
    }

    @Test
    void lateWebhookFailure_neverOverwritesCapturedSession() {
        WalletTopupSession s = session(WalletTopupStatus.CAPTURED);
        s.setProviderPaymentId("pay_1");

        deliver("payment.failed", "pay_1", ",\"error_description\":\"declined\"");

        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        assertThat(s.getFailureReason()).isNull();
    }

    @Test
    void secondCaptureEventWithAnotherPaymentId_doesNotCreditAgain_orOverwriteTheId() {
        WalletTopupSession s = session(WalletTopupStatus.CAPTURED);
        s.setProviderPaymentId("pay_1");

        deliver("payment.captured", "pay_2", ",\"amount\":50000,\"currency\":\"INR\"");

        assertThat(s.getProviderPaymentId()).isEqualTo("pay_1");
        verify(walletCreditClient, never()).credit(any());
    }

    @Test
    void failedAttemptThenSuccessfulRetry_isStillCredited() {
        WalletTopupSession s = session(WalletTopupStatus.INITIATED);

        deliver("payment.failed", "pay_a", ",\"error_description\":\"declined\"");
        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.FAILED);
        deliver("payment.captured", "pay_b", ",\"amount\":50000,\"currency\":\"INR\"");

        assertThat(s.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        verify(walletCreditClient).credit(new WalletCreditCommand("topup-1", "cust-1", "org-a", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR")));
    }

    @Test
    void createSession_callsTheGatewayWithNoTransaction_afterPersistingTheInitiatedSession() {
        boolean[] active = {true};
        when(sessionRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(resolver.resolveCreateSession(any())).thenReturn(createClient);
        when(createClient.createSession(any(Payment.class))).thenAnswer(inv -> {
            active[0] = TransactionSynchronizationManager.isActualTransactionActive();
            assertThat(savedStatuses).containsExactly(WalletTopupStatus.INITIATED);
            return GatewaySessionResult.ofRazorpay("order_9", null);
        });

        var response = service.createTopupSession(new CreateWalletTopupRequest("cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR"), "k", null));

        assertThat(active[0]).isFalse();
        assertThat(response.providerOrderId()).isEqualTo("order_9");
    }

    @Test
    void createSession_gatewayFailure_marksTheSessionFailed() {
        when(sessionRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(resolver.resolveCreateSession(any())).thenReturn(createClient);
        when(createClient.createSession(any(Payment.class))).thenThrow(new PaymentServiceException("down"));

        assertThatThrownBy(() -> service.createTopupSession(new CreateWalletTopupRequest("cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR"), "k", null)))
                .isInstanceOf(PaymentServiceException.class);

        assertThat(savedStatuses).containsExactly(WalletTopupStatus.INITIATED, WalletTopupStatus.FAILED);
    }

    @Test
    void replayOfAnotherCustomersKey_isRejected() {
        WalletTopupSession existing = session(WalletTopupStatus.INITIATED);
        when(sessionRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createTopupSession(new CreateWalletTopupRequest("cust-2", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR"), "k", null)))
                .isInstanceOf(ConflictException.class);
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
