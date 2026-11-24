package com.heuristiq.notification.adapters.out.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.notification.domain.model.AuditOutboxStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mongo document of the audit outbox. See the domain {@code AuditOutboxEntry}. */
@Document(collection = "nms_audit_outbox")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditOutboxDocument {

    @Id
    private String id;

    private String eventId;

    private String payload;

    private AuditOutboxStatus status;

    private int attempts;

    private LocalDateTime nextAttemptAt;

    private LocalDateTime lockedUntil;

    private String lockOwner;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    private LocalDateTime expireAt;
}
