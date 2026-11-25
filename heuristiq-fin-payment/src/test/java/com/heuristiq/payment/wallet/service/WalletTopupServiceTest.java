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
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import com.heuristiq.payment.wallet.dto.CreateWalletTopupRequest;
import com.heuristiq.payment.wallet.dto.WalletTopupResponse;
import com.heuristiq.payment.wallet.model.WalletTopupSession;
import com.heuristiq.payment.wallet.model.WalletTopupStatus;
import com.heuristiq.payment.wallet.repository.WalletTopupSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;

/**
 * WalletTopupService — proves the real, gateway-verified replacement for WalletController's old
 * insecure {@code POST /{customerId}/topup} (which credited the wallet off a bare caller-supplied
 * amount, no gateway involved at all). Covers: idempotent session creation, webhook signature
 * rejection, webhook-driven capture crediting the wallet exactly once, and the PAYU/TOUCAN/
 * PINELABS poll path (captured/failed/pending/errored/expired) — mirroring
 * PaymentServiceStatusPollingTest's coverage shape for the ride-payment equivalent.
 */
class WalletTopupServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test_dummy_secret";

    private WalletTopupSessionRepository walletTopupSessionRepository;
    private PaymentWebhookEventRepository webhookEventRepository;
    private WalletCreditClient walletCreditClient;
    private WalletTopupSession current;
    private PaymentGatewayClientResolver gatewayClientResolver;
    private CreateSessionGatewayClient createSessionGatewayClient;
    private StatusCheckGatewayClient statusCheckGatewayClient;
    private PaymentPollingProperties paymentPollingProperties;
    private RazorpayWebhookSignatureVerifier webhookSignatureVerifier;
    private WalletTopupService walletTopupService;

    @BeforeEach
    void setUp() {
        var caller = new com.heuristiq.payment.security.AuthPrincipal("cust-1", "CUSTOMER", false, "org-a");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(caller, null, List.of()));
        walletTopupSessionRepository = mock(WalletTopupSessionRepository.class);
        webhookEventRepository = mock(PaymentWebhookEventRepository.class);
        walletCreditClient = mock(WalletCreditClient.class);
        when(walletCreditClient.credit(any(WalletCreditCommand.class))).thenReturn("journal-1");
        gatewayClientResolver = mock(PaymentGatewayClientResolver.class);
        createSessionGatewayClient = mock(CreateSessionGatewayClient.class);
        statusCheckGatewayClient = mock(StatusCheckGatewayClient.class);
        paymentPollingProperties = new PaymentPollingProperties();
        paymentPollingProperties.setMaxAgeHours(24);

        RazorpayProperties razorpayProperties = new RazorpayProperties();
        razorpayProperties.setWebhookSecret(WEBHOOK_SECRET);
        webhookSignatureVerifier = new RazorpayWebhookSignatureVerifier(razorpayProperties);

        walletTopupService = new WalletTopupService(
                walletTopupSessionRepository, webhookEventRepository,
                new WalletTopupCreditService(walletTopupSessionRepository, walletCreditClient,
                        new WalletServiceProperties(), java.time.Clock.systemUTC()),
                gatewayClientResolver, new PaymentGatewayRoutingProperties(), paymentPollingProperties,
                webhookSignatureVerifier, new GatewayAmountVerifier(new com.heuristiq.payment.config.MoneyProperties()), mock(WebhookAuditRecorder.class));

        when(walletTopupSessionRepository.save(any(WalletTopupSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletTopupSessionRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(current));
    }

    @org.junit.jupiter.api.AfterEach
    void clearCaller() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void topupWithoutAnOrganizationCannotStartGatewayCheckout() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        var amount = com.heuristiq.payment.shared.money.Money.of(BigDecimal.TEN, "INR");
        assertThatThrownBy(() -> walletTopupService.createTopupSession(new CreateWalletTopupRequest("cust-1", amount, "missing-org", null)))
                .isInstanceOf(com.heuristiq.payment.shared.exception.ConflictException.class);
        org.mockito.Mockito.verifyNoInteractions(gatewayClientResolver, walletTopupSessionRepository);
    }

    @Test
    void topupSnapshotsTheOriginAppAndRejectsAnIdempotencyKeyFromAnotherCustomer() {
        when(gatewayClientResolver.resolveCreateSession(PaymentGatewayType.RAZORPAY)).thenReturn(createSessionGatewayClient);
        when(createSessionGatewayClient.createSession(any(Payment.class))).thenReturn(GatewaySessionResult.ofRazorpay("order-origin", null));
        var amount = com.heuristiq.payment.shared.money.Money.of(new BigDecimal("100"), "INR");
        walletTopupService.createTopupSession(new CreateWalletTopupRequest("cust-1", amount, "origin-key", null, "FOOD_DELIVER_APP"));
        var saved = org.mockito.ArgumentCaptor.forClass(WalletTopupSession.class);
        verify(walletTopupSessionRepository, times(2)).save(saved.capture());
        assertThat(saved.getValue().getAppCode()).isEqualTo("FOOD_DELIVER_APP");
        when(walletTopupSessionRepository.findByIdempotencyKey("origin-key")).thenReturn(Optional.of(saved.getValue()));
        assertThatThrownBy(() -> walletTopupService.createTopupSession(
                new CreateWalletTopupRequest("cust-2", amount, "origin-key", null, "FOOD_DELIVER_APP")))
                .isInstanceOf(com.heuristiq.payment.shared.exception.ConflictException.class);
        assertThatThrownBy(() -> walletTopupService.createTopupSession(
                new CreateWalletTopupRequest("cust-1", amount, "origin-key", null, "BESTRIDE_RIDER_APP")))
                .isInstanceOf(com.heuristiq.payment.shared.exception.ConflictException.class);
    }

    @Test
    void createTopupSession_defaultsToRazorpay_andReturnsCheckoutSession() {
        when(walletTopupSessionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(gatewayClientResolver.resolveCreateSession(PaymentGatewayType.RAZORPAY)).thenReturn(createSessionGatewayClient);
        when(createSessionGatewayClient.createSession(any(Payment.class)))
                .thenReturn(GatewaySessionResult.ofRazorpay("order_abc", null));

        WalletTopupResponse response = walletTopupService.createTopupSession(
                new CreateWalletTopupRequest("cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR"), "idem-1", null));

        assertThat(response.status()).isEqualTo(WalletTopupStatus.INITIATED.name());
        assertThat(response.gateway()).isEqualTo("RAZORPAY");
        assertThat(response.providerOrderId()).isEqualTo("order_abc");
        // Persisted INITIATED first, then saved again with the gateway order id.
        verify(walletTopupSessionRepository, times(2)).save(any(WalletTopupSession.class));
    }

    @Test
    void createTopupSession_idempotentReplay_doesNotCreateANewGatewaySession() {
        WalletTopupSession existing = WalletTopupSession.builder().tenantId("org-a")
                .id("topup-1").customerId("cust-1").amount(new BigDecimal("500.0")).currency("INR")
                .gateway(PaymentGatewayType.RAZORPAY).status(WalletTopupStatus.INITIATED)
                .idempotencyKey("idem-1").createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        when(walletTopupSessionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        WalletTopupResponse response = walletTopupService.createTopupSession(
                new CreateWalletTopupRequest("cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR"), "idem-1", null));

        assertThat(response.topupId()).isEqualTo("topup-1");
        verify(gatewayClientResolver, never()).resolveCreateSession(any());
        verify(walletTopupSessionRepository, never()).save(any(WalletTopupSession.class));
    }

    @Test
    void topupWebhook_captured_creditsWalletExactlyOnce_evenIfDeliveredTwice() {
        WalletTopupSession session = WalletTopupSession.builder()
                .id("topup-1").customerId("cust-1").amount(new BigDecimal("500.0")).currency("INR")
                .gateway(PaymentGatewayType.RAZORPAY).status(WalletTopupStatus.INITIATED)
                .providerOrderId("order_abc123").idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        current = session;
        when(walletTopupSessionRepository.findByProviderOrderId("order_abc123")).thenReturn(Optional.of(session));

        java.util.Map<String, PaymentWebhookEvent> store = new java.util.HashMap<>();
        when(webhookEventRepository.findByProviderEventId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(store.get((String) inv.getArgument(0))));
        when(webhookEventRepository.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent event = inv.getArgument(0);
            store.put(event.getProviderEventId(), event);
            return event;
        });

        String rawBody = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_xyz789","order_id":"order_abc123","amount":50000,"currency":"INR"}}}}""";
        String signature = hmacSha256Hex(rawBody, WEBHOOK_SECRET);

        walletTopupService.handleTopupWebhook(rawBody, signature, null);
        walletTopupService.handleTopupWebhook(rawBody, signature, null);

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        verify(walletCreditClient, times(1)).credit(new WalletCreditCommand("topup-1", "cust-1", null, com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.0"), "INR")));
    }

    @Test
    void topupWebhook_invalidSignature_rejectedAndNeverCreditsWallet() {
        String rawBody = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_xyz789","order_id":"order_abc123","amount":50000,"currency":"INR"}}}}""";

        assertThatThrownBy(() -> walletTopupService.handleTopupWebhook(rawBody, "not-the-right-signature", null))
                .isInstanceOf(PaymentServiceException.class);

        verify(walletCreditClient, never()).credit(any());
    }

    private WalletTopupSession payuSession(WalletTopupStatus status, LocalDateTime createdAt) {
        return WalletTopupSession.builder()
                .id("topup-1").customerId("cust-1").amount(new BigDecimal("300.0")).currency("INR")
                .gateway(PaymentGatewayType.PAYU).status(status)
                .idempotencyKey("idem-2").createdAt(createdAt).updatedAt(createdAt)
                .build();
    }

    @Test
    void pollPendingTopups_gatewayReportsSuccess_creditsWallet() {
        WalletTopupSession session = payuSession(WalletTopupStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        current = session;
        when(walletTopupSessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", "provider-pay-1", "SUCCESS", "captured", new BigDecimal("300.0"), "INR"));

        walletTopupService.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        verify(walletCreditClient, times(1)).credit(new WalletCreditCommand("topup-1", "cust-1", null, com.heuristiq.payment.shared.money.Money.of(new BigDecimal("300.0"), "INR")));
    }

    @Test
    void pollPendingTopups_gatewayReportsFailed_doesNotCreditWallet() {
        WalletTopupSession session = payuSession(WalletTopupStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        current = session;
        when(walletTopupSessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "FAILED", "declined", new BigDecimal("300.0"), "INR"));

        walletTopupService.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.FAILED);
        verify(walletCreditClient, never()).credit(any());
    }

    @Test
    void pollPendingTopups_gatewayReportsPending_leavesSessionUnchanged() {
        WalletTopupSession session = payuSession(WalletTopupStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        current = session;
        when(walletTopupSessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "order-1", null, "PENDING", "in_progress", new BigDecimal("300.0"), "INR"));

        walletTopupService.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.INITIATED);
        verify(walletTopupSessionRepository, never()).save(any(WalletTopupSession.class));
    }

    @Test
    void pollPendingTopups_statusCheckThrows_leavesSessionUnchanged() {
        WalletTopupSession session = payuSession(WalletTopupStatus.INITIATED, LocalDateTime.now().minusMinutes(5));
        current = session;
        when(walletTopupSessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(gatewayClientResolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusCheckGatewayClient);
        when(statusCheckGatewayClient.fetchStatus(any(Payment.class))).thenThrow(new RuntimeException("gateway unreachable"));

        walletTopupService.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.INITIATED);
        verify(walletTopupSessionRepository, never()).save(any(WalletTopupSession.class));
    }

    @Test
    void pollPendingTopups_tooOld_expiresWithoutCallingGateway() {
        WalletTopupSession session = payuSession(WalletTopupStatus.INITIATED, LocalDateTime.now().minusHours(48));
        current = session;
        when(walletTopupSessionRepository.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));

        walletTopupService.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.EXPIRED);
        verify(gatewayClientResolver, never()).resolveStatusCheck(any());
        verify(walletCreditClient, never()).credit(any());
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
