package com.heuristiq.payment.payment.model;

import com.heuristiq.payment.shared.PaymentCommonConstants;
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
 * One refund of a {@link Payment}: the refund ledger. The unique {@code refundReference} makes refund
 * requests idempotent, and the sum of PROCESSING and COMPLETED rows is the cumulative amount already
 * committed against the payment.
 */
@Entity
@Table(name = "payment_refunds")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefund {

    @Id
    private String id;

    @Column(name = "refund_reference", unique = true, nullable = false)
    private String refundReference;

    /** Caller-supplied Idempotency-Key header value; unique when present. */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    /** Currency (ISO 4217) of {@code amount}; set exactly when that amount is set (V24). */
    @Column(name = "amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String amountCurrency;

    @Enumerated(EnumType.STRING)
    private RefundDestination destination;

    @Enumerated(EnumType.STRING)
    private RefundStatus status;

    @Column(name = "gateway_refund_id")
    private String gatewayRefundId;

    @Column(name = "gateway_status")
    private String gatewayStatus;

    /** The Rider_WalletService journal that holds the credit of a WALLET-destination refund, once booked. */
    @Column(name = "wallet_journal_id")
    private String walletJournalId;

    @Column(length = 500)
    private String reason;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
