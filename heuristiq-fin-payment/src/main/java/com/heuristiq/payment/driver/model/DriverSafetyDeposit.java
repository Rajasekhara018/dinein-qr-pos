package com.heuristiq.paymentservice.driver.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "driver_safety_deposits")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverSafetyDeposit {
    @Id
    private String id;
    private String driverId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;
    /** Currency (ISO 4217) of {@code amount}; set exactly when that amount is set (V24). */
    @Column(name = "amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String amountCurrency;

    private String paymentMode; // cash, online, bank_transfer
    @Enumerated(EnumType.STRING)
    @Default
    private DepositStatus status = DepositStatus.PENDING;
    private String transactionId;
    private LocalDateTime createdAt;
}
