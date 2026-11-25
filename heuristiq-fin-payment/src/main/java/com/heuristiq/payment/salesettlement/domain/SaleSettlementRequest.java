package com.heuristiq.payment.salesettlement.domain;

import java.time.Instant;

/**
 * One stored request to settle or cancel a wallet sale.
 *
 * @param id             row id (order of arrival)
 * @param eventId        the wallet sale's event id
 * @param action         settle or cancel
 * @param idempotencyKey the wallet idempotency key ({@code sale-settle:<eventId>} / {@code sale-cancel:<eventId>})
 * @param paymentId      the payment whose status change asked for it
 * @param tenantId       the payment's organization (sent as {@code X-Tenant-Id}); null when the payment has none
 * @param reason         why a sale is cancelled (null for a settlement)
 * @param attempts       failed sends so far (waiting for the sale to be booked is not a failure and is not counted)
 * @param createdAt      when the request was stored: a SETTLE whose sale is still not booked after {@code await-sale-max-age}
 *                       is given up. Null when unknown (then it is never given up for its age)
 */
public record SaleSettlementRequest(long id, String eventId, SaleSettlementAction action, String idempotencyKey, String paymentId,
                                    String tenantId, String reason, int attempts, Instant createdAt) {

    /** A request whose storage time is not known. */
    public SaleSettlementRequest(long id, String eventId, SaleSettlementAction action, String idempotencyKey, String paymentId,
                                 String tenantId, String reason, int attempts) {
        this(id, eventId, action, idempotencyKey, paymentId, tenantId, reason, attempts, null);
    }
}
