package com.heuristiq.paymentservice.refundaccounting;

import com.heuristiq.paymentservice.refundaccounting.application.RefundAccountingService;
import com.heuristiq.paymentservice.refundaccounting.domain.RefundAccounting;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class RefundAccountingServiceTest {
    @Test
    void anUnconfirmedJournalIsRetriedWithTheSameEventAndCreditFlag() {
        var store = mock(RefundAccounting.Store.class);
        var gateway = mock(RefundAccounting.Gateway.class);
        var now = Instant.parse("2026-11-01T00:00:00Z");
        var service = new RefundAccountingService(store, gateway, Clock.fixed(now, ZoneOffset.UTC));
        var posting = new RefundAccounting.Posting("refund:1", "org-a", "ride-completed:1", new BigDecimal("50"), "INR", true, 0);
        service.record(posting);
        verify(store).enqueue(posting);
        when(store.claim(now)).thenReturn(List.of(posting));
        doThrow(new IllegalStateException("Wallet unavailable")).doNothing().when(gateway).post(posting);
        service.deliverDue();
        verify(store).retry(posting, now.plusSeconds(30), "Wallet unavailable");
        verify(store, never()).delivered(any());
        service.deliverDue();
        verify(store).delivered(posting);
        verify(gateway, times(2)).post(posting);
    }

    @Test
    void aLegacyRefundWithoutAnOrganizationRemainsQueuedForBackfill() {
        var store = mock(RefundAccounting.Store.class);
        var gateway = mock(RefundAccounting.Gateway.class);
        var now = Instant.parse("2026-11-01T00:00:00Z");
        var posting = new RefundAccounting.Posting("refund:1", null, "ride-completed:1", BigDecimal.TEN, "INR", false, 50);
        when(store.claim(now)).thenReturn(List.of(posting));
        new RefundAccountingService(store, gateway, Clock.fixed(now, ZoneOffset.UTC)).deliverDue();
        verifyNoInteractions(gateway);
        verify(store).retry(posting, now.plusSeconds(3600), "The refund requires organization backfill");
    }
}
