package com.heuristiq.documents.expiry.application;

import java.time.LocalDate;

/** Port: tells a document's owner (the driver) that it expires soon or has lapsed (D12). */
public interface ExpiryNotifier {

    /**
     * @param daysLeft   days from today to the expiry date (negative once lapsed)
     * @param daysBefore the reminder (days before expiry), or {@code DocumentValidityConstants.LAPSED_NOTICE} once lapsed
     * @return true when the notification service accepted it; false leaves the reminder for the next run
     */
    boolean notify(String tenantId, String ownerId, String documentId, String category, LocalDate validUntil, long daysLeft,
                   int daysBefore);
}
