package com.heuristiq.payment.refundaccounting.application;

import com.heuristiq.payment.refundaccounting.domain.RefundAccounting;
import static com.heuristiq.payment.refundaccounting.domain.RefundAccountingConstants.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RefundAccountingService {
    private static final Logger log = LoggerFactory.getLogger(RefundAccountingService.class);
    private final RefundAccounting.Store store;
    private final RefundAccounting.Gateway gateway;
    private final Clock clock;

    public RefundAccountingService(RefundAccounting.Store store, RefundAccounting.Gateway gateway, Clock clock) {
        this.store = store;
        this.gateway = gateway;
        this.clock = clock;
    }

    /** Called in the transaction recording COMPLETED, so accounting work cannot be lost. */
    public void record(RefundAccounting.Posting posting) {
        store.enqueue(posting);
    }

    public void deliverDue() {
        for (var posting : store.claim(clock.instant())) {
            try {
                if (posting.tenantId() == null || posting.tenantId().isBlank()) {
                    throw new IllegalStateException("The refund requires organization backfill");
                }
                gateway.post(posting);
                store.delivered(posting);
            } catch (RuntimeException error) {
                store.retry(posting, clock.instant().plusSeconds(Math.min(MAX_RETRY_SECONDS, INITIAL_RETRY_SECONDS << Math.min(posting.attempts(), MAX_BACKOFF_SHIFT))),
                        error.getMessage());
                log.warn("Refund accounting {} remains queued: {}",
                        posting.refundEventId(), error.getMessage());
            }
        }
    }
}
