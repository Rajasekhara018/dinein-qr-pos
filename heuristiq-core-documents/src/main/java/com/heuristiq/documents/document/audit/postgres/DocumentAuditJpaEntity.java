package com.railcab.dms.document.audit.postgres;

import com.railcab.dms.document.audit.DocumentAuditAction;
import com.railcab.dms.document.audit.DocumentAuditEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "dms_document_audit_events", indexes = {
        @Index(name = "idx_dms_audit_document_id", columnList = "documentId"),
        @Index(name = "idx_dms_audit_object_key", columnList = "objectKey"),
        @Index(name = "idx_dms_audit_owner_id", columnList = "ownerId")
})
public class DocumentAuditJpaEntity {

    @Id
    private String id;

    private String documentId;
    private String objectKey;
    private String ownerId;
    private String category;

    @Enumerated(EnumType.STRING)
    private DocumentAuditAction action;

    private String actorId;
    private String actorRole;
    private String status;
    private String message;
    private Instant occurredAt;

    public static DocumentAuditJpaEntity fromDomain(DocumentAuditEvent event) {
        DocumentAuditJpaEntity entity = new DocumentAuditJpaEntity();
        entity.id = event.getId();
        entity.documentId = event.getDocumentId();
        entity.objectKey = event.getObjectKey();
        entity.ownerId = event.getOwnerId();
        entity.category = event.getCategory();
        entity.action = event.getAction();
        entity.actorId = event.getActorId();
        entity.actorRole = event.getActorRole();
        entity.status = event.getStatus();
        entity.message = event.getMessage();
        entity.occurredAt = event.getOccurredAt();
        return entity;
    }

    public DocumentAuditEvent toDomain() {
        DocumentAuditEvent event = new DocumentAuditEvent();
        event.setId(id);
        event.setDocumentId(documentId);
        event.setObjectKey(objectKey);
        event.setOwnerId(ownerId);
        event.setCategory(category);
        event.setAction(action);
        event.setActorId(actorId);
        event.setActorRole(actorRole);
        event.setStatus(status);
        event.setMessage(message);
        event.setOccurredAt(occurredAt);
        return event;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
