package com.heuristiq.paymentservice.driver.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Driver-earnings ledger row for one ride's fare, mapped onto the copied driver_payments table. */
@Entity
@Table(name = "driver_payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverPayment {
    @Id
    private String id;
    private String driverId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;
    private String rideId;
    /** Gross fare collected from the customer */
    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;
    /** Currency (ISO 4217) of {@code amount}; set exactly when that amount is set (V24). */
    @Column(name = "amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String amountCurrency;

    /**
     * Whether {@code platformFee}/{@code netAmount} are the ride's own split (V25). Null only on a row built in memory before it
     * is saved; rows written before V25 read as SPLIT.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "split_status", length = 20)
    private DriverSplitStatus splitStatus;

    /** The ride split's platform fee as a percentage of {@code amount} (derived for display; null until the split is known). */
    @Column(name = "platform_fee_percent", precision = 7, scale = 3)
    private BigDecimal platformFeePercent;
    /** Platform commission of the ride as its stored split says; null while {@code splitStatus} is not SPLIT. */
    @Column(name = "platform_fee", precision = 14, scale = 2)
    private BigDecimal platformFee;
    /** Currency (ISO 4217) of {@code platformFee}; set exactly when that amount is set (V24). */
    @Column(name = "platform_fee_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String platformFeeCurrency;

    /** Driver's net earning = amount - platformFee (the ride split's driver share); null while the split is not known. */
    @Column(name = "net_amount", precision = 14, scale = 2)
    private BigDecimal netAmount;
    /** Currency (ISO 4217) of {@code netAmount}; set exactly when that amount is set (V24). */
    @Column(name = "net_amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String netAmountCurrency;

    private LocalDateTime createdAt;
}
