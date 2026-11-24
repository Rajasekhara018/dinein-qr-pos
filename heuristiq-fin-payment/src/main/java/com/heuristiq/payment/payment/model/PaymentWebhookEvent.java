package com.heuristiq.payment.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Records every Razorpay webhook event this service has processed, keyed by the
 * provider's own event id. HLD §18/§23.3: payment webhooks MUST be idempotent — a
 * retried delivery (Razorpay retries webhooks that don't 2xx quickly, and can also
 * redeliver duplicates) must not double-apply the state transition or double-run
 * side effects (driver-earnings computation, Ride-service notification).
 */
@Entity
@Table(name = "payment_webhook_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentWebhookEvent {

    @Id
    private String id;

    @Column(name = "provider_event_id", unique = true)
    private String providerEventId;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /** Gateway that sent the delivery (e.g. RAZORPAY). */
    @Column(name = "provider", length = 32)
    private String provider;

    /** Whether the delivery's signature verified; the signature itself is never stored. */
    @Column(name = "signature_valid")
    private Boolean signatureValid;

    /** PROCESSED, IGNORED, DUPLICATE or REJECTED (see {@code WebhookOutcome}). */
    @Column(name = "outcome", length = 16)
    private String outcome;

    /**
     * The event id the delivery claimed. Equals {@code providerEventId} for processed rows; DUPLICATE and
     * REJECTED rows leave {@code providerEventId} null so the dedupe key stays reserved for the processed one.
     */
    @Column(name = "claimed_event_id", length = 255)
    private String claimedEventId;
}
