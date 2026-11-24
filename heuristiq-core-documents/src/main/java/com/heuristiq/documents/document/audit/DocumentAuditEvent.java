package com.railcab.dms.document.audit;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "dms_document_audit_events")
public class DocumentAuditEvent {

    @Id
    private String id;

    @Indexed
    private String documentId;

    @Indexed
    private String objectKey;

    @Indexed
    private String ownerId;

    private String category;
    private DocumentAuditAction action;
    private String actorId;
    private String actorRole;
    private String status;
    private String message;

    private Instant occurredAt;
}
