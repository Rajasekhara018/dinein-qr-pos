package com.heuristiq.payment.salesettlement.domain;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Port: Rider_WalletService's {@code POST /internal/sales/{eventId}/settle} and {@code /cancel}, and its sale lookup
 * ({@code POST /internal/sales/lookups}), asked before a settlement is sent so that a settlement is sent only for a booked sale.
 */
public interface WalletSaleSettlementPort {

    /** How the wallet answered. */
    enum Result {
        /** Settled or cancelled now, or a replay of an earlier one. */
        DONE,
        /** 404: no sale is booked for the event (yet). */
        SALE_NOT_FOUND,
        /** 409 SALE_NOT_PENDING: the sale was settled or cancelled already. */
        NOT_PENDING,
        /** 400: the wallet refuses the request as such; sending it again will not help. */
        REJECTED,
        /** Anything else (5xx, timeouts, an open breaker, 409 BUSY, an unreadable answer): the outcome is unknown, retry. */
        UNAVAILABLE
    }

    /**
     * @param result    the wallet's answer
     * @param journalId the wallet journal of a DONE answer, when it sent one
     * @param detail    the error code or a short description of a failed answer
     */
    record Answer(Result result, String journalId, String detail) {
    }

    /** Sends the request once (the resilient caller may repeat it on a timeout; it is keyed, so that is safe). */
    Answer send(SaleSettlementRequest request);

    /**
     * Asks the wallet which of the given sales (at most {@link SaleSettlementConstants#MAX_LOOKUP_EVENTS}) are booked in the
     * organization. A read, safe to repeat.
     *
     * @return the event ids that have a SALE journal, or empty when the wallet could not answer (outage or an error answer)
     */
    Optional<Set<String>> bookedSales(String tenantId, Collection<String> eventIds);
}
