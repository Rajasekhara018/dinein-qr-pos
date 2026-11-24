package com.heuristiq.payment.wallet.service;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.payment.config.WalletServiceProperties;
import com.heuristiq.payment.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.payment.wallet.model.WalletTopupSession;
import com.heuristiq.payment.wallet.model.WalletTopupStatus;
import com.heuristiq.payment.wallet.port.WalletCreditClient;
import com.heuristiq.payment.wallet.port.WalletCreditCommand;
import com.heuristiq.payment.wallet.port.WalletCreditRejectedException;
import com.heuristiq.payment.wallet.port.WalletTenantMissingException;
import com.heuristiq.payment.wallet.repository.WalletTopupSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The wallet credit of a paid top-up: one idempotent call keyed by the top-up id, a refusal or outage keeps the top-up
 * awaiting credit with a capped backoff, the retry job is bounded and never double-credits, and the organization travels
 * with the call.
 */
class WalletTopupCreditServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    private WalletTopupSessionRepository sessions;
    private WalletCreditClient wallet;
    private WalletServiceProperties properties;
    private WalletTopupCreditService service;
    private WalletTopupSession session;

    @BeforeEach
    void setUp() {
        sessions = mock(WalletTopupSessionRepository.class);
        wallet = mock(WalletCreditClient.class);
        properties = new WalletServiceProperties();
        service = new WalletTopupCreditService(sessions, wallet, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        session = paid("topup-1");
        when(sessions.findById("topup-1")).thenReturn(Optional.of(session));
        when(sessions.save(any(WalletTopupSession.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static WalletTopupSession paid(String id) {
        return WalletTopupSession.builder().id(id).customerId("cust-1").tenantId("org-a")
                .amount(new BigDecimal("500.00")).currency("INR").gateway(PaymentGatewayType.RAZORPAY)
                .status(WalletTopupStatus.PAID_AWAITING_CREDIT).idempotencyKey("idem-" + id)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void successfulCredit_capturesTheTopup_andSendsTheTopupIdUserAmountAndOrganization() {
        when(wallet.credit(any())).thenReturn("journal-9");

        boolean credited = service.attemptCredit("topup-1");

        assertThat(credited).isTrue();
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        assertThat(session.getWalletJournalId()).isEqualTo("journal-9");
        assertThat(session.getCreditedAt()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(session.getCreditLastError()).isNull();
        verify(wallet).credit(new WalletCreditCommand("topup-1", "cust-1", "org-a", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.00"), "INR")));
    }

    @Test
    void missingJournalEvidenceLeavesPaidMoneyAwaitingCredit() {
        when(wallet.credit(any())).thenReturn(null);
        assertThat(service.attemptCredit("topup-1")).isFalse();
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getCreditLastError()).isEqualTo(WalletConstants.REASON_MISSING_JOURNAL);
    }

    @Test
    void retryKeepsTheSessionsOriginApp() {
        session.setAppCode("FOOD_DELIVER_APP");
        when(wallet.credit(any())).thenReturn("j-origin");
        assertThat(service.attemptCredit("topup-1")).isTrue();
        verify(wallet).credit(new WalletCreditCommand("topup-1", "cust-1", "org-a",
                com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.00"), "INR"), "FOOD_DELIVER_APP"));
    }

    @Test
    void walletRefusesWith409PurchaseDisabled_topupStaysPaidAwaitingCredit_withBackoff() {
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));

        boolean credited = service.attemptCredit("topup-1");

        assertThat(credited).isFalse();
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getCreditAttempts()).isEqualTo(1);
        assertThat(session.getCreditLastError()).isEqualTo("PURCHASE_DISABLED");
        assertThat(session.getCreditNextAttemptAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(30));
    }

    @Test
    void walletOutage_topupStaysPaidAwaitingCredit() {
        when(wallet.credit(any())).thenThrow(new DownstreamUnavailableException("wallet-service",
                DownstreamUnavailableException.Reason.TIMEOUT, new RuntimeException("timeout")));

        assertThat(service.attemptCredit("topup-1")).isFalse();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getCreditLastError()).isEqualTo("WALLET_UNAVAILABLE");
    }

    @Test
    void refusalWithoutAnErrorCode_isRecordedByHttpStatus() {
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(401, null));

        service.attemptCredit("topup-1");

        assertThat(session.getCreditLastError()).isEqualTo("HTTP_401");
    }

    @Test
    void unexpectedError_isRecordedAndNeverEscapes() {
        when(wallet.credit(any())).thenThrow(new IllegalStateException("boom"));

        assertThat(service.attemptCredit("topup-1")).isFalse();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getCreditLastError()).isEqualTo("UNEXPECTED_IllegalStateException");
    }

    @Test
    void backoffDoublesEachFailure_andIsCapped() {
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        service.attemptCredit("topup-1");
        assertThat(session.getCreditNextAttemptAt()).isEqualTo(now.plusSeconds(30));
        service.attemptCredit("topup-1");
        assertThat(session.getCreditNextAttemptAt()).isEqualTo(now.plusSeconds(60));
        service.attemptCredit("topup-1");
        assertThat(session.getCreditNextAttemptAt()).isEqualTo(now.plusSeconds(120));

        session.setCreditAttempts(60);
        service.attemptCredit("topup-1");
        assertThat(session.getCreditNextAttemptAt()).isEqualTo(now.plus(Duration.ofSeconds(3600)));
    }

    @Test
    void anAlreadyCreditedTopup_isNeverCreditedAgain() {
        session.setStatus(WalletTopupStatus.CAPTURED);

        boolean result = service.attemptCredit("topup-1");

        assertThat(result).isTrue();
        verify(wallet, never()).credit(any());
    }

    @Test
    void aTopupThatWasNeverPaid_isNeverCredited() {
        for (WalletTopupStatus status : List.of(WalletTopupStatus.INITIATED, WalletTopupStatus.FAILED,
                WalletTopupStatus.EXPIRED, WalletTopupStatus.AMOUNT_MISMATCH)) {
            session.setStatus(status);
            assertThat(service.attemptCredit("topup-1")).isFalse();
        }
        verify(wallet, never()).credit(any());
    }

    @Test
    void anUnknownTopup_isIgnored() {
        when(sessions.findById("nope")).thenReturn(Optional.empty());

        assertThat(service.attemptCredit("nope")).isFalse();
        verify(wallet, never()).credit(any());
    }

    @Test
    void sameTopupCreditedTwice_sendsTheSameKeyBothTimes_andTheWalletReplayCapturesOnce() {
        when(wallet.credit(any())).thenReturn("journal-1");

        service.attemptCredit("topup-1");
        // a duplicate webhook/poll arriving after the status update does not call the wallet again
        service.attemptCredit("topup-1");

        verify(wallet, times(1)).credit(any());
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
    }

    @Test
    void creditLostRaceToAnotherPath_reportsWhatThatPathStored() {
        when(wallet.credit(any())).thenReturn("journal-1");
        WalletTopupSession winner = paid("topup-1");
        winner.setStatus(WalletTopupStatus.CAPTURED);
        when(sessions.findById("topup-1")).thenReturn(Optional.of(session), Optional.of(winner));
        when(sessions.save(any(WalletTopupSession.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThat(service.attemptCredit("topup-1")).isTrue();
    }

    @Test
    void failedCreditThatLostARace_doesNotOverwriteTheNewerState() {
        when(wallet.credit(any())).thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));
        when(sessions.save(any(WalletTopupSession.class))).thenThrow(new OptimisticLockingFailureException("stale"));

        assertThat(service.attemptCredit("topup-1")).isFalse();
    }

    @Test
    void correlationIdIsSetForTheCallWhenThereIsNone_andRemovedAfterwards() {
        MDC.remove("correlationId");
        String[] seen = new String[1];
        when(wallet.credit(any())).thenAnswer(inv -> {
            seen[0] = MDC.get("correlationId");
            return "j";
        });

        service.attemptCredit("topup-1");

        assertThat(seen[0]).isEqualTo("topup-credit-topup-1");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void anInboundCorrelationIdIsKept() {
        MDC.put("correlationId", "req-42");
        try {
            String[] seen = new String[1];
            when(wallet.credit(any())).thenAnswer(inv -> {
                seen[0] = MDC.get("correlationId");
                return "j";
            });

            service.attemptCredit("topup-1");

            assertThat(seen[0]).isEqualTo("req-42");
            assertThat(MDC.get("correlationId")).isEqualTo("req-42");
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    void creditIsDueOnlyWhenPaidAndTheBackoffHasRunOut() {
        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        assertThat(service.isCreditDue(session)).isTrue();
        session.setCreditNextAttemptAt(now.plusSeconds(1));
        assertThat(service.isCreditDue(session)).isFalse();
        session.setCreditNextAttemptAt(now);
        assertThat(service.isCreditDue(session)).isTrue();
        session.setStatus(WalletTopupStatus.CAPTURED);
        assertThat(service.isCreditDue(session)).isFalse();
    }

    // ---- retry job ----

    @Test
    void retryJob_creditsEveryDueTopup_inOneBoundedBatch() {
        WalletTopupSession second = paid("topup-2");
        when(sessions.findById("topup-2")).thenReturn(Optional.of(second));
        when(sessions.findCreditDue(any(), anyInt())).thenReturn(List.of(session, second));
        when(wallet.credit(any())).thenReturn("j");

        int credited = service.retryDueCredits();

        assertThat(credited).isEqualTo(2);
        verify(sessions).findCreditDue(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), properties.getCreditRetryBatchSize());
        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
        assertThat(second.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
    }

    @Test
    void retryJob_oneStuckTopupDoesNotStopTheOthers() {
        WalletTopupSession second = paid("topup-2");
        when(sessions.findById("topup-2")).thenReturn(Optional.of(second));
        when(sessions.findCreditDue(any(), anyInt())).thenReturn(List.of(session, second));
        when(wallet.credit(new WalletCreditCommand("topup-1", "cust-1", "org-a", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.00"), "INR"))))
                .thenThrow(new WalletCreditRejectedException(409, "PURCHASE_DISABLED"));
        when(wallet.credit(new WalletCreditCommand("topup-2", "cust-1", "org-a", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("500.00"), "INR"))))
                .thenReturn("j2");

        assertThat(service.retryDueCredits()).isEqualTo(1);

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(second.getStatus()).isEqualTo(WalletTopupStatus.CAPTURED);
    }

    @Test
    void retryJob_runTwice_creditsOnce() {
        when(sessions.findCreditDue(any(), anyInt())).thenReturn(List.of(session));
        when(wallet.credit(any())).thenReturn("j");

        service.retryDueCredits();
        service.retryDueCredits();

        verify(wallet, times(1)).credit(any());
    }

    @Test
    void retryJob_withNothingDue_callsNoone() {
        when(sessions.findCreditDue(any(), anyInt())).thenReturn(List.of());

        assertThat(service.retryDueCredits()).isZero();
        verify(wallet, never()).credit(any());
    }

    @Test
    void aTopupWithNoOrganization_isNeverCredited_andStaysAwaitingWithTheReason() {
        session.setTenantId(null);
        when(wallet.credit(any())).thenThrow(new WalletTenantMissingException("top-up topup-1"));

        assertThat(service.attemptCredit("topup-1")).isFalse();

        assertThat(session.getStatus()).isEqualTo(WalletTopupStatus.PAID_AWAITING_CREDIT);
        assertThat(session.getCreditLastError()).contains("WalletTenantMissingException");
    }
}
