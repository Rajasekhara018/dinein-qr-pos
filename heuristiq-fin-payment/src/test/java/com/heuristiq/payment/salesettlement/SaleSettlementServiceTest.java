package com.heuristiq.payment.salesettlement;

import com.heuristiq.payment.salesettlement.application.SaleSettlementService;
import com.heuristiq.payment.salesettlement.config.SaleSettlementProperties;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementAction;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementOutbox;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementRequest;
import com.heuristiq.payment.salesettlement.domain.SaleSettlementState;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort.Answer;
import com.heuristiq.payment.salesettlement.domain.WalletSaleSettlementPort.Result;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** How a settle or cancel request is recorded and how each wallet answer finishes or retries it. */
class SaleSettlementServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    private final SaleSettlementOutbox outbox = mock(SaleSettlementOutbox.class);
    private final WalletSaleSettlementPort wallet = mock(WalletSaleSettlementPort.class);
    private final SaleSettlementProperties properties = new SaleSettlementProperties(true, 10, Duration.ofSeconds(30),
            Duration.ofMinutes(10), 5);
    private final SaleSettlementService service = new SaleSettlementService(outbox, wallet, properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static SaleSettlementRequest settle(int attempts) {
        return new SaleSettlementRequest(1, "ride-completed:r-1", SaleSettlementAction.SETTLE, "sale-settle:ride-completed:r-1",
                "p-1", "t1", null, attempts);
    }

    private static SaleSettlementRequest cancel() {
        return new SaleSettlementRequest(2, "ride-completed:r-1", SaleSettlementAction.CANCEL, "sale-cancel:ride-completed:r-1",
                "p-1", "t1", "Gateway payment failed", 0);
    }

    private void due(SaleSettlementRequest request) {
        when(outbox.due(NOW, 10)).thenReturn(List.of(request));
        booked("t1", request.eventId());
    }

    /** The wallet lookup of the organization answers these sales as booked. */
    private void booked(String tenantId, String... eventIds) {
        when(wallet.bookedSales(eq(tenantId), any())).thenReturn(Optional.of(Set.of(eventIds)));
    }

    @Test
    void aCaptureQueuesTheSaleSettlementUnderTheContractKey() {
        when(outbox.enqueue(anyString(), any(), anyString(), anyString(), any(), any())).thenReturn(true);

        assertThat(service.paymentCaptured("p-1", "t1", "matrimonial-api", "txn-1", null)).isTrue();

        verify(outbox).enqueue("MATRIMONY:SALE:txn-1", SaleSettlementAction.SETTLE, "sale-settle:MATRIMONY:SALE:txn-1", "p-1", "t1", null);
    }

    @Test
    void aFailureQueuesTheSaleCancellationWithItsReason() {
        when(outbox.enqueue(anyString(), any(), anyString(), anyString(), any(), any())).thenReturn(true);

        service.paymentAbandoned("p-1", "t1", null, null, "r-1", "Gateway payment failed");

        verify(outbox).enqueue("ride-completed:r-1", SaleSettlementAction.CANCEL, "sale-cancel:ride-completed:r-1", "p-1", "t1",
                "Gateway payment failed");
    }

    @Test
    void aPaymentForNoKnownSaleOrADisabledSwitchQueuesNothing() {
        assertThat(service.paymentCaptured("p-1", "t1", "train-service", "x", null)).isFalse();
        SaleSettlementService off = new SaleSettlementService(outbox, wallet,
                new SaleSettlementProperties(false, 10, Duration.ofSeconds(30), Duration.ofMinutes(10), 5), Clock.systemUTC());
        assertThat(off.paymentCaptured("p-1", "t1", null, null, "r-1")).isFalse();
        assertThat(off.sendDue()).isZero();
        verifyNoInteractions(outbox, wallet);
    }

    @Test
    void aSettledSaleFinishesTheRequestWithTheJournal() {
        due(settle(0));
        when(wallet.send(any())).thenReturn(new Answer(Result.DONE, "j-1", null));

        assertThat(service.sendDue()).isEqualTo(1);

        verify(outbox).finish(1, SaleSettlementState.DONE, "SETTLED", "j-1", null, NOW);
    }

    @Test
    void aSaleReversedBetweenTheLookupAndTheSettlementWaitsWithoutCountingAnAttempt() {
        due(settle(2));
        when(wallet.send(any())).thenReturn(new Answer(Result.SALE_NOT_FOUND, null, "SALE_NOT_FOUND_YET"));

        assertThat(service.sendDue()).isZero();

        verify(outbox).awaitSale(1, "AWAITING_SALE", NOW.plus(Duration.ofMinutes(2)));
        verify(outbox, never()).retryLater(anyLong(), anyInt(), any(), any());
        verify(outbox, never()).finish(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void theBackoffIsCapped() {
        due(settle(3));
        when(wallet.send(any())).thenReturn(new Answer(Result.UNAVAILABLE, null, "HTTP_503"));
        SaleSettlementService capped = new SaleSettlementService(outbox, wallet,
                new SaleSettlementProperties(true, 10, Duration.ofSeconds(30), Duration.ofSeconds(60), 50), Clock.fixed(NOW, ZoneOffset.UTC));

        capped.sendDue();

        verify(outbox).retryLater(1, 4, "HTTP_503", NOW.plusSeconds(60));
    }

    @Test
    void aSaleAlreadySettledOrCancelledIsDone() {
        due(settle(0));
        when(wallet.send(any())).thenReturn(new Answer(Result.NOT_PENDING, null, "SALE_NOT_PENDING"));

        service.sendDue();

        verify(outbox).finish(1, SaleSettlementState.DONE, "SALE_NOT_PENDING", null, null, NOW);
    }

    @Test
    void aRefusedRequestFailsAtOnce() {
        due(settle(0));
        when(wallet.send(any())).thenReturn(new Answer(Result.REJECTED, null, "VALIDATION_ERROR"));

        service.sendDue();

        verify(outbox).finish(1, SaleSettlementState.FAILED, null, null, "VALIDATION_ERROR", NOW);
    }

    @Test
    void aRequestIsGivenUpAfterMaxAttempts() {
        due(settle(4));
        when(wallet.send(any())).thenReturn(new Answer(Result.UNAVAILABLE, null, "WALLET_UNAVAILABLE"));

        service.sendDue();

        verify(outbox).finish(eq(1L), eq(SaleSettlementState.FAILED), isNull(), isNull(), eq("GAVE_UP_AFTER_MAX_ATTEMPTS WALLET_UNAVAILABLE"),
                eq(NOW));
        verify(outbox, never()).retryLater(anyLong(), anyInt(), any(), any());
    }

    @Test
    void anUnexpectedErrorIsRetriedNotLost() {
        due(settle(0));
        when(wallet.send(any())).thenThrow(new IllegalStateException("boom"));

        service.sendDue();

        verify(outbox).retryLater(1, 1, "UNEXPECTED: IllegalStateException", NOW.plusSeconds(30));
    }

    @Test
    void aPaymentWithoutOrganizationIsNeverSent() {
        due(new SaleSettlementRequest(1, "e", SaleSettlementAction.SETTLE, "k", "p-1", null, null, 0));

        service.sendDue();

        verify(outbox).finish(1, SaleSettlementState.FAILED, null, null, "TENANT_MISSING", NOW);
        verifyNoInteractions(wallet);
    }

    @Test
    void aCancellationOfASalePaidAfterAllIsSuperseded() {
        due(cancel());
        when(outbox.salePaidAfterAll("p-1")).thenReturn(true);

        assertThat(service.sendDue()).isEqualTo(1);

        verify(outbox).finish(2, SaleSettlementState.SUPERSEDED, "PAYMENT_CAPTURED", null, null, NOW);
        verifyNoInteractions(wallet);
    }

    @Test
    void aCancellationWithNoSaleBookedIsDone() {
        due(cancel());
        when(wallet.send(any())).thenReturn(new Answer(Result.SALE_NOT_FOUND, null, "SALE_NOT_FOUND_YET"));

        service.sendDue();

        verify(outbox).finish(2, SaleSettlementState.DONE, "NO_SALE", null, null, NOW);
    }

    @Test
    void aCancelledSaleFinishesWithTheReversal() {
        due(cancel());
        when(wallet.send(any())).thenReturn(new Answer(Result.DONE, "rev-1", null));

        service.sendDue();

        verify(outbox).finish(2, SaleSettlementState.DONE, "CANCELLED", "rev-1", null, NOW);
    }

    private static SaleSettlementRequest settleOf(long id, String eventId, String tenantId, Instant createdAt) {
        return new SaleSettlementRequest(id, eventId, SaleSettlementAction.SETTLE, "sale-settle:" + eventId, "p-" + id, tenantId, null,
                0, createdAt);
    }

    @Test
    void aSaleNotBookedYetWaitsWithoutCallingSettleOrCountingAnAttempt() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settleOf(1, "ride-completed:r-1", "t1", NOW.minusSeconds(60))));
        booked("t1");

        assertThat(service.sendDue()).isZero();

        verify(outbox).awaitSale(1, "AWAITING_SALE", NOW.plus(Duration.ofMinutes(2)));
        verify(wallet, never()).send(any());
        verify(outbox, never()).retryLater(anyLong(), anyInt(), any(), any());
    }

    @Test
    void aSaleStillNotBookedAfterTheMaximumWaitIsGivenUp() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settleOf(1, "ride-completed:r-1", "t1", NOW.minus(Duration.ofDays(7)))));
        booked("t1");

        service.sendDue();

        verify(outbox).finish(1, SaleSettlementState.FAILED, null, null, "SALE_NEVER_BOOKED", NOW);
        verify(outbox, never()).awaitSale(anyLong(), any(), any());
        verify(wallet, never()).send(any());
    }

    @Test
    void aRequestOfUnknownAgeWaitsRatherThanBeingGivenUp() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settleOf(1, "e-1", "t1", null)));
        booked("t1");

        service.sendDue();

        verify(outbox).awaitSale(1, "AWAITING_SALE", NOW.plus(Duration.ofMinutes(2)));
    }

    @Test
    void aLookupTheWalletCannotAnswerIsAFailedAttemptRetriedWithBackoff() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settle(0)));
        when(wallet.bookedSales(eq("t1"), any())).thenReturn(Optional.empty());

        assertThat(service.sendDue()).isZero();

        verify(outbox).retryLater(1, 1, "SALE_LOOKUP_UNAVAILABLE", NOW.plusSeconds(30));
        verify(wallet, never()).send(any());
    }

    @Test
    void aLookupThatThrowsIsRetriedTheSameWay() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settle(0)));
        when(wallet.bookedSales(eq("t1"), any())).thenThrow(new IllegalStateException("boom"));

        service.sendDue();

        verify(outbox).retryLater(1, 1, "SALE_LOOKUP_UNAVAILABLE", NOW.plusSeconds(30));
    }

    @Test
    void eachOrganizationIsLookedUpOnceWithOnlyItsOwnSalesAndCancellationsAreNotLookedUp() {
        when(outbox.due(NOW, 10)).thenReturn(List.of(settleOf(1, "e-1", "org-a", NOW), settleOf(2, "e-2", "org-b", NOW),
                settleOf(3, "e-3", "org-a", NOW), new SaleSettlementRequest(4, "e-4", SaleSettlementAction.CANCEL, "sale-cancel:e-4",
                        "p-4", "org-a", "Gateway payment failed", 0)));
        when(wallet.bookedSales(eq("org-a"), any())).thenReturn(Optional.of(Set.of("e-1")));
        when(wallet.bookedSales(eq("org-b"), any())).thenReturn(Optional.of(Set.of("e-2")));
        when(wallet.send(any())).thenReturn(new Answer(Result.DONE, "j", null));

        assertThat(service.sendDue()).isEqualTo(3);

        verify(wallet).bookedSales("org-a", List.of("e-1", "e-3"));
        verify(wallet).bookedSales("org-b", List.of("e-2"));
        verify(wallet, org.mockito.Mockito.times(3)).send(any());
        verify(outbox).awaitSale(3, "AWAITING_SALE", NOW.plus(Duration.ofMinutes(2)));
    }

    @Test
    void anOrganizationWithMoreSalesThanOneLookupTakesIsAskedInChunks() {
        List<SaleSettlementRequest> many = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            many.add(settleOf(i, "e-" + i, "t1", NOW));
        }
        SaleSettlementService big = new SaleSettlementService(outbox, wallet,
                new SaleSettlementProperties(true, 200, Duration.ofSeconds(30), Duration.ofMinutes(10), 5), Clock.fixed(NOW, ZoneOffset.UTC));
        when(outbox.due(NOW, 200)).thenReturn(many);
        List<Integer> sizes = new ArrayList<>();
        when(wallet.bookedSales(eq("t1"), any())).thenAnswer(inv -> {
            sizes.add(inv.<Collection<String>>getArgument(1).size());
            return Optional.of(Set.of());
        });

        big.sendDue();

        assertThat(sizes).containsExactly(100, 50);
    }

    @Test
    void theSaleWaitSettingsAreValidated() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SaleSettlementProperties(true, 1, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1, Duration.ZERO, Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new SaleSettlementProperties(true, 1, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 1, Duration.ofMinutes(5), Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SaleSettlementProperties(true, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), 1).awaitSaleMaxAge())
                .isEqualTo(Duration.ofDays(7));
    }
}
