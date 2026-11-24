package com.heuristiq.paymentservice.driver.model;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Ported from Rider_BookingsAPI's {@code driver.incentive.DriverIncentive} — same field shape
 * (ruleName, description, bonusAmount, weekStartDate, awardedAt, status), now owned by this
 * service alongside the rest of driver earnings/payouts (HLD's payment/billing split).
 */
@Entity
@Table(name = "driver_incentives")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverIncentive {

    @Id
    private String id;

    @Column(name = "driver_id")
    private String driverId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "rule_name")
    private String ruleName;

    private String description;

    @Column(name = "bonus_amount", precision = 14, scale = 2)
    private BigDecimal bonusAmount;
    /** Currency (ISO 4217) of {@code bonusAmount}; set exactly when that amount is set (V24). */
    @Column(name = "bonus_amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String bonusAmountCurrency;

    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "awarded_at")
    private LocalDateTime awardedAt;

    @Builder.Default
    private String status = "PENDING";
}
