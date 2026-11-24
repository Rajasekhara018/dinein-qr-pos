package com.railcab.nms.adapters.out.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.railcab.nms.domain.model.AuditOutboxStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JPA row of the audit outbox. See the domain {@code AuditOutboxEntry}. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nms_audit_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_nms_audit_outbox_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_nms_audit_outbox_due", columnList = "status, next_attempt_at"),
                @Index(name = "idx_nms_audit_outbox_expire", columnList = "expire_at")
        })
public class AuditOutboxEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(nullable = false, length = 8000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditOutboxStatus status;

    private int attempts;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    private LocalDateTime lockedUntil;

    private String lockOwner;

    @Column(length = JpaPersistenceConstants.AUDIT_OUTBOX_ERROR_COLUMN_LENGTH)
    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;
}
