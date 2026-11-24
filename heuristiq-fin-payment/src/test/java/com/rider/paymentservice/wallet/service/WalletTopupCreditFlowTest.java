package com.heuristiq.paymentservice.wallet.service;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.paymentservice.config.PaymentGatewayRoutingProperties;
import com.heuristiq.paymentservice.config.PaymentPollingProperties;
import com.heuristiq.paymentservice.config.RazorpayProperties;
import com.heuristiq.paymentservice.config.WalletServiceProperties;
import com.heuristiq.paymentservice.gatewayaudit.WebhookAuditRecorder;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.razorpay.RazorpayWebhookSignatureVerifier;
import com.heuristiq.paymentservice.payment.gateway.resolver.PaymentGatewayClientResolver;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentWebhookEvent;
import com.heuristiq.paymentservice.payment.repository.PaymentWebhookEventRepository;
import com.heuristiq.paymentservice.payment.service.GatewayAmountVerifier;
import com.heuristiq.paymentservice.security.AuthPrincipal;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import com.heuristiq.paymentservice.wallet.dto.WalletTopupResponse;
import com.heuristiq.paymentservice.wallet.model.WalletTopupSession;
import com.heuristiq.paymentservice.wallet.model.WalletTopupStatus;
import com.heuristiq.paymentservice.wallet.port.WalletCreditClient;
import com.heuristiq.paymentservice.wallet.port.WalletCreditCommand;
import com.heuristiq.paymentservice.wallet.port.WalletCreditRejectedException;
import com.heuristiq.paymentservice.wallet.repository.WalletTopupSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The top-up success flow end to end with a faked wallet: webhook and poll mark the top-up paid, the wallet is credited
 * by ONE idempotent call, a wallet outage or 409 leaves the top-up PAID_AWAITING_CREDIT (never lost, never credited
 * twice) and GET /topup/{id} reports that state until the wallet holds the credit.
 */
class WalletTopupCreditFlowTest {

    private static final String SECRET = "whsec_test_dummy_secret";
    private static final String BODY = """
            {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_1","order_id":"order_1","amount":50000,"currency":"INR"}}}}""";

    private WalletTopupSessionRepository sessions;
    private PaymentWebhookEventRepository events;
    private WalletCreditClient wallet;
    private PaymentGatewayClientResolver resolver;
    private StatusCheckGatewayClient statusClient;
    private WalletTopupService topups;
    private WalletTopupCreditService credits;
    private WalletTopupSession session;
    private final Map<String, PaymentWebhookEvent> eventStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        sessions = mock(WalletTopupSessionRepository.class);
        events = mock(PaymentWebhookEventRepository.class);
        wallet = mock(WalletCreditClient.class);
        resolver = mock(PaymentGatewayClientResolver.class);
        statusClient = mock(StatusCheckGatewayClient.class);
        RazorpayProperties razorpay = new RazorpayProperties();
        razorpay.setWebhookSecret(SECRET);
        credits = new WalletTopupCreditService(sessions, wallet, new WalletServiceProperties(), Clock.systemUTC());
        topups = new WalletTopupService(sessions, events, credits, resolver, new PaymentGatewayRoutingProperties(),
                new PaymentPollingProperties(), new RazorpayWebhookSignatureVerifier(razorpay), new GatewayAmountVerifier(new com.heuristiq.paymentservice.config.MoneyProperties()),
                mock(WebhookAuditRecorder.class));

        session = WalletTopupSession.builder().id("topup-1").customerId("cust-1").tenantId("org-a")
                .amount(new BigDecimal("500.00")).currency("INR").gateway(PaymentGatewayType.RAZORPAY)
                .status(WalletTopupStatus.INITIATED).providerOrderId("order_1").idempotencyKey("idem-1")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(sessions.findById("topup-1")).thenReturn(Optional.of(session));
        when(sessions.findByProviderOrderId("order_1")).thenReturn(Optional.of(session));
        when(sessions.save(any(WalletTopupSession.class))).thenAnswer(inv -> inv.getArgument(0));
        when(events.findByProviderEventId(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(eventStore.get((String) inv.getArgument(0))));
        when(events.save(any(PaymentWebhookEvent.class))).thenAnswer(inv -> {
            PaymentWebhookEvent e = inv.getArgument(0);
            eventStore.put(e.getProviderEventId(), e);
            return e;
        });
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void deliverWebhook(String eventId) {
        topups.handleTopupWebhook(BODY, hmac(BODY), eventId);
    }

    @Test
    void webhookPaid_walletCredited_topupBecomesCaptured() {
        when(wallet.credit(any())).thenReturn("journal-1");

        deliverWebhook("evt-1");

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        assertThat(session.getProviderPaymentId()).isEqualTo("pay_1");
        verify(wallet).credit(new WalletCreditCommand("topup-1", "cust-1", "org-a", com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("500.00"), "INR")));
    }

    @Test
    void duplicateWebhookDelivery_callsTheWalletOnce() {
        when(wallet.credit(any())).thenReturn("journal-1");

        deliverWebhook("evt-1");
        deliverWebhook("evt-1");
        // a second, differently-numbered event for the same payment (Razorpay also sends order.paid)
        deliverWebhook("evt-2");

        verify(wallet, times(1)).credit(any());
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
    }

    @Test
    void walletRefusesWith409_webhookStillSucceeds_andTheTopupWaitsForCredit() {
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));

        deliverWebhook("evt-1");

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getProviderPaymentId()).isEqualTo("pay_1");
        assertThat(session.getCreditAttempts()).isEqualTo(1);
        assertThat(session.getCreditLastError()).isEqualTo("PURCHASE_DISABLED");
        // the webhook was recorded as processed, so the gateway is not told to redeliver
        assertThat(eventStore).containsKey("evt-1");
    }

    @Test
    void walletDown_thenAnotherEventForTheSamePayment_doesNotCreditAndDoesNotRevertTheState() {
        when(wallet.credit(any())).thenThrow(new DownstreamUnavailableException("wallet-service",
                DownstreamUnavailableException.Reason.CIRCUIT_OPEN, new RuntimeException("open")));

        deliverWebhook("evt-1");
        deliverWebhook("evt-2");

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        verify(wallet, times(1)).credit(any());
    }

    @Test
    void getTopup_whileCreditPending_reportsPaidAwaitingCredit_andDoesNotCallTheWalletBeforeTheBackoffEnds() {
        session.setStatus(WalletTopupStatus.PAID_AWAITING_CREDIT);
        session.setCreditNextAttemptAt(LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10));

        WalletTopupResponse response = topups.getTopup("topup-1");

        assertThat(response.status()).isEqualTo("PAID_AWAITING_CREDIT");
        assertThat(response.topupId()).isEqualTo("topup-1");
        assertThat(response.amount().value()).isEqualByComparingTo("500.00");
        verify(wallet, never()).credit(any());
    }

    @Test
    void getTopup_whenTheBackoffHasEnded_triesTheCreditAndReportsCaptured() {
        session.setStatus(WalletTopupStatus.PAID_AWAITING_CREDIT);
        session.setCreditNextAttemptAt(LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1));
        when(wallet.credit(any())).thenReturn("journal-3");

        WalletTopupResponse response = topups.getTopup("topup-1");

        assertThat(response.status()).isEqualTo("CAPTURED");
        verify(wallet, times(1)).credit(any());
    }

    @Test
    void getTopup_repeatedPollsOfACreditedTopup_neverCallTheWalletAgain() {
        session.setStatus(WalletTopupStatus.CAPTURED);

        topups.getTopup("topup-1");
        topups.getTopup("topup-1");

        verify(wallet, never()).credit(any());
    }

    @Test
    void duplicatePoll_creditsOnce() {
        session.setGateway(PaymentGatewayType.PAYU);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(sessions.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(resolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusClient);
        when(statusClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "o", "pay-9", "SUCCESS", "captured", new BigDecimal("500.0"), "INR"));
        when(wallet.credit(any())).thenReturn("journal-1");

        topups.pollPendingTopups();
        topups.pollPendingTopups();

        verify(wallet, times(1)).credit(any());
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
    }

    @Test
    void pollPaid_walletDown_leavesTheTopupAwaitingCredit_andThePollDoesNotRetryItAsAGatewayCapture() {
        session.setGateway(PaymentGatewayType.PAYU);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        when(sessions.findByStatusInAndGatewayIn(any(), any())).thenReturn(List.of(session));
        when(resolver.resolveStatusCheck(PaymentGatewayType.PAYU)).thenReturn(statusClient);
        when(statusClient.fetchStatus(any(Payment.class))).thenReturn(
                new GatewayStatusResult("PAYU", "o", "pay-9", "SUCCESS", "captured", new BigDecimal("500.0"), "INR"));
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));

        topups.pollPendingTopups();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        verify(wallet, times(1)).credit(any());
    }

    @Test
    void awaitingCredit_thenTheWalletRecovers_theRetryJobCreditsItOnce() {
        when(wallet.credit(any()))
                .thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"))
                .thenReturn("journal-5");
        deliverWebhook("evt-1");
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        when(sessions.findCreditDue(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(session), List.of());

        assertThat(credits.retryDueCredits()).isEqualTo(1);
        assertThat(credits.retryDueCredits()).isZero();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        assertThat(session.getWalletJournalId()).isEqualTo("journal-5");
        verify(wallet, times(2)).credit(any());
    }

    @Test
    void getTopup_ofAnotherOrganization_isNotFound() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal("cust-9", "CUSTOMER", false, "org-b"), null, List.of()));

        assertThatThrownBy(() -> topups.getTopup("topup-1")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getTopup_ofTheCallersOrganization_isReturned() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new AuthPrincipal("cust-1", "CUSTOMER", false, "org-a"), null, List.of()));
        session.setGateway(PaymentGatewayType.PAYU);

        assertThat(topups.getTopup("topup-1").topupId()).isEqualTo("topup-1");
    }

    private static String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
