package com.heuristiq.payment.splitfare.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.payment.shared.PaymentCommonConstants;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;
import jakarta.persistence.Column;

@Entity
@Table(name = "split_fare_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitFareRequest {

    @Id
    private String id;

    private String rideId;
    private String initiatorUserId;

    @jakarta.persistence.Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;
    /** Currency (ISO 4217) of {@code totalAmount}; set exactly when that amount is set (V24). Not in the
     * response yet: this entity is returned as is, and the API shape changes only in the money stage 3. */
    @JsonIgnore
    @Column(name = "total_amount_currency", length = PaymentCommonConstants.CURRENCY_CODE_LENGTH)
    private String totalAmountCurrency;

    @ElementCollection
    @CollectionTable(name = "split_fare_participants", joinColumns = @JoinColumn(name = "split_fare_request_id"))
    @OrderColumn(name = "participant_order")
    private List<SplitParticipant> participants;

    @Builder.Default
    private String status = "PENDING";

    private LocalDateTime createdAt;
}
