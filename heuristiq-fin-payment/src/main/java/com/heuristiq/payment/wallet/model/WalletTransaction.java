package com.heuristiq.payment.wallet.model;

import com.heuristiq.payment.shared.PaymentCommonConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/** JPA entity mapped 1:1 onto the copied wallet_transactions table (see V1__baseline_schema.sql). */
@Entity
@Table(name = "wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {
    @Id
    private String id;
    private String userId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;
    /** Currency (ISO 4217) of {@code amount}; set exactly when that amount is set (V24). */
    @Column(name = "amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String amountCurrency;

    /** CREDIT or DEBIT */
    private String type;

    /** TOPUP | RIDE_PAYMENT | REFUND | PROMO */
    private String source;

    private String razorpayOrderId;
    private String razorpayPaymentId;

    /** External reference: rideId for RIDE_PAYMENT, paymentId for REFUND. */
    private String referenceId;

    /** PENDING | SUCCESS | FAILED */
    @Default
    private String status = "PENDING";

    private String description;
    private LocalDateTime createdAt;

    /**
     * Business-event key that makes a posting happen at most once, for example {@code REFUND:<refund
     * reference>}, {@code TOPUP:<topup session id>} or {@code RIDE_PAYMENT:<ride id>}. Null on rows
     * written before the key existed; the unique index ignores nulls.
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;
}
