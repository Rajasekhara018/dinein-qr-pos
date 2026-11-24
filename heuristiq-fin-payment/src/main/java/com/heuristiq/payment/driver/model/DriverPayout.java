package com.heuristiq.paymentservice.driver.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
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
import jakarta.persistence.Column;

@Entity
@Table(name = "driver_payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverPayout {
    @Id
    private String id;

    private String driverId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "requested_amount", precision = 14, scale = 2)
    private BigDecimal requestedAmount;
    /** Currency (ISO 4217) of {@code requestedAmount}; set exactly when that amount is set (V24). */
    @Column(name = "requested_amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String requestedAmountCurrency;

    /** PENDING | PROCESSING | COMPLETED | FAILED */
    @Default
    private String status = "PENDING";

    // Snapshot of bank details supplied at request time (this service owns no driver-identity data).
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;
    private String bankName;

    /** UTR/reference number filled by admin after processing */
    private String utrNumber;
    private String adminNotes;

    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
}
