package com.heuristiq.payment.splitfare.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.payment.shared.PaymentCommonConstants;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import jakarta.persistence.Column;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitParticipant {
    private String phoneNumber;
    private String name;
    @Column(name = "share_amount", precision = 14, scale = 2)
    private BigDecimal shareAmount;
    /** Currency (ISO 4217) of {@code shareAmount}; set exactly when that amount is set (V24). Not in the
     * response yet: this entity is returned as is, and the API shape changes only in the money stage 3. */
    @JsonIgnore
    @Column(name = "share_amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String shareAmountCurrency;

    @Builder.Default
    private String paymentStatus = "PENDING";

    private LocalDateTime paidAt;
}
