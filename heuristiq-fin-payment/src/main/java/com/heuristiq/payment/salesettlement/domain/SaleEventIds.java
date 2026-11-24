package com.heuristiq.paymentservice.salesettlement.domain;

import java.util.Optional;

/** Derives the wallet sale event id a payment pays for (table in {@link SaleSettlementConstants}). Pure functions. */
public final class SaleEventIds {

    private SaleEventIds() {
    }

    /**
     * @param sourceService   the application that raised the payment, or null/blank for a ride payment
     * @param sourceReference that application's own id of what is paid for
     * @param rideId          the ride of a ride payment
     * @return the sale event id, or empty when the payment pays for no wallet sale this service knows
     */
    public static Optional<String> of(String sourceService, String sourceReference, String rideId) {
        if (blank(sourceService)) {
            return blank(rideId) ? Optional.empty() : Optional.of(SaleSettlementConstants.RIDE_SALE_PREFIX + rideId.trim());
        }
        if (blank(sourceReference)) {
            return Optional.empty();
        }
        String reference = sourceReference.trim();
        return switch (sourceService.trim()) {
            case SaleSettlementConstants.MATRIMONY_SOURCE_SERVICE -> Optional.of(SaleSettlementConstants.MATRIMONY_SALE_PREFIX + reference);
            case SaleSettlementConstants.FOOD_SOURCE_SERVICE -> Optional.of(SaleSettlementConstants.FOOD_SALE_PREFIX + reference);
            default -> Optional.empty();
        };
    }

    /** @return the wallet idempotency key of this action on this sale */
    public static String idempotencyKey(SaleSettlementAction action, String eventId) {
        return (action == SaleSettlementAction.SETTLE ? SaleSettlementConstants.SETTLE_KEY_PREFIX
                : SaleSettlementConstants.CANCEL_KEY_PREFIX) + eventId;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
