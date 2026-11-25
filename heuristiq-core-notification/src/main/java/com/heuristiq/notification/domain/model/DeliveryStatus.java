package com.heuristiq.notification.domain.model;

/**
 * Lifecycle of one delivery attempt (and, as a summary, of the per-recipient log row).
 *
 * <p>{@code SENT}, {@code FAILED} and {@code SKIPPED} are the original values and keep their meaning,
 * so rows written before the per-attempt model still load. {@code QUEUED}, {@code DELIVERED},
 * {@code BOUNCED} and {@code READ} are new and only appear once a provider receipt reports them.
 *
 * <p>Receipts can arrive duplicated or out of order, so a status may only move forward: see
 * {@link #canAdvanceTo(DeliveryStatus)}.
 */
public enum DeliveryStatus {
    QUEUED(0),
    SENT(1),
    FAILED(2),
    BOUNCED(2),
    DELIVERED(3),
    READ(4),
    /** Nothing was attempted (no provider, template or recipient). Never changed by a receipt. */
    SKIPPED(-1);

    private final int rank;

    DeliveryStatus(int rank) {
        this.rank = rank;
    }

    /** True when the provider confirmed the message reached the device or mailbox. */
    public boolean isDelivered() {
        return this == DELIVERED || this == READ;
    }

    /** True for the states that mean the message did not get through. */
    public boolean isFailure() {
        return this == FAILED || this == BOUNCED;
    }

    /**
     * Whether a receipt reporting {@code next} may replace {@code current}. Equal or lower ranks are
     * duplicates or late arrivals and are ignored, which makes receipt handling idempotent.
     * A {@code null} current status (legacy rows) accepts anything except {@code SKIPPED}.
     */
    public static boolean canAdvance(DeliveryStatus current, DeliveryStatus next) {
        if (next == null || next == SKIPPED || current == SKIPPED) {
            return false;
        }
        return current == null || next.rank > current.rank;
    }

    /** Instance form of {@link #canAdvance(DeliveryStatus, DeliveryStatus)}. */
    public boolean canAdvanceTo(DeliveryStatus next) {
        return canAdvance(this, next);
    }
}
