package com.heuristq.dinein.payment.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Raw webhook log for audit and de-duplication by (provider, provider event id). */
@Getter
@Setter
@Entity
@Table(name = "payment_event")
public class PaymentEventEntity extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_event_id", length = 100)
    private String providerEventId;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "signature_valid", nullable = false)
    private boolean signatureValid;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error", length = 500)
    private String processingError;
}
