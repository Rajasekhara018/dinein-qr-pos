package com.heuristiq.paymentservice.wallet.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * A real, gateway-verified wallet top-up — INITIATED (checkout session created) -> CAPTURED
 * (gateway confirmed, then Rider_WalletService credited) or FAILED/EXPIRED. Deliberately separate from the
 * {@code payments} table: that table's {@code ride_id} is NOT NULL and every ride-payment code
 * path (webhook handling, status polling, receipts) assumes a real ride, neither of which a
 * wallet top-up has. Payment keeps only the gateway side of a top-up; the balance and ledger are Wallet's.
 * See WalletTopupService for the flow.
 */
@Entity
@Table(name = "wallet_topup_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTopupSession {

    @Id
    private String id;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "tenant_id")
    private String tenantId;

    /** Immutable origin application, retained when a paid credit is retried by a background job. */
    @Column(name = "app_code", length = 100)
    private String appCode;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Builder.Default
    private String currency = PaymentCommonConstants.DEFAULT_CURRENCY;

    @Enumerated(EnumType.STRING)
    private PaymentGatewayType gateway;

    @Enumerated(EnumType.STRING)
    private WalletTopupStatus status;

    @Column(name = "provider_order_id")
    private String providerOrderId;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Optimistic lock: a late webhook, a poll and the credit job never overwrite each other's update. */
    @Version
    private Long version;

    /** Failed wallet-credit attempts so far (0 until the first one fails). */
    @Column(name = "credit_attempts", nullable = false)
    @Builder.Default
    private int creditAttempts = 0;

    /** Earliest time the credit job may try again; null means "now" (a paid top-up not yet attempted). */
    @Column(name = "credit_next_attempt_at")
    private LocalDateTime creditNextAttemptAt;

    /** Why the last credit attempt failed (wallet error code or reason), for support; cleared on success. */
    @Column(name = "credit_last_error")
    private String creditLastError;

    @Column(name = "credited_at")
    private LocalDateTime creditedAt;

    /** The wallet journal that holds the credit. */
    @Column(name = "wallet_journal_id")
    private String walletJournalId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
