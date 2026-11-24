package com.heuristiq.documents.documenttype.adapter.out.postgres;

import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.VerificationMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Postgres shape of a {@link DocumentType}. Boxed numeric/boolean fields so a query-by-example probe can leave them
 * unset; the schema is created by hbm2ddl like the rest of DMS. Code is unique per organization.
 */
@Entity
@Table(name = "dms_document_types",
        uniqueConstraints = @UniqueConstraint(name = "uq_dms_document_types_tenant_code", columnNames = {"tenantId", "code"}),
        indexes = {
                @Index(name = "idx_dms_document_types_tenant_category", columnList = "tenantId,category"),
                @Index(name = "idx_dms_document_types_tenant_flow", columnList = "tenantId,flow")
        })
class DocumentTypeJpaEntity {

    @Id
    String id;
    @Column(nullable = false)
    String tenantId;
    @Column(nullable = false, length = 60)
    String code;
    @Column(nullable = false, length = 60)
    String category;
    @Column(length = 60)
    String flow;
    @Column(columnDefinition = "text")
    String names;
    @Column(length = 200)
    String allowedFormats;
    Integer maxFileSizeMb;
    Boolean required;
    Integer expiryYears;
    /** Null on rows stored before the column existed: read as "service default". */
    Integer retentionDays;
    /** Null on rows stored before the column existed: read as true (scan required). */
    Boolean scanRequired;
    /** Null on rows stored before the column existed: read as false (no verification). */
    Boolean verificationRequired;
    /** Null (or unknown) on rows stored before the column existed: read as NONE. */
    @Column(length = 40)
    String verificationMethod;
    @Column(length = 40)
    String verificationProvider;
    Boolean active;
    @Version
    Long version;
    Instant createdAt;
    Instant updatedAt;
    String createdBy;
    String updatedBy;
    String lastChangeRequestId;
    @Column(columnDefinition = "text")
    String recentChangeRequestIds;

    static DocumentTypeJpaEntity fromDomain(DocumentType type) {
        DocumentTypeJpaEntity entity = new DocumentTypeJpaEntity();
        entity.id = type.id();
        entity.tenantId = type.tenantId();
        entity.code = type.code();
        entity.category = type.category();
        entity.flow = type.flow();
        entity.names = DocumentTypeColumnCodec.namesToJson(type.names());
        entity.allowedFormats = DocumentTypeColumnCodec.join(type.allowedFormats().stream().map(Enum::name).toList());
        entity.maxFileSizeMb = type.maxFileSizeMb();
        entity.required = type.required();
        entity.expiryYears = type.expiryYears();
        entity.retentionDays = type.retentionDays();
        entity.scanRequired = type.scanRequired();
        entity.verificationRequired = type.verificationRequired();
        entity.verificationMethod = type.verificationMethod().name();
        entity.verificationProvider = type.verificationProvider();
        entity.active = type.active();
        entity.version = type.version();
        entity.createdAt = type.createdAt();
        entity.updatedAt = type.updatedAt();
        entity.createdBy = type.createdBy();
        entity.updatedBy = type.updatedBy();
        entity.lastChangeRequestId = type.lastChangeRequestId();
        entity.recentChangeRequestIds = DocumentTypeColumnCodec.join(type.recentChangeRequestIds());
        return entity;
    }

    /** A probe for query-by-example: only the organization and the optional filters are set. */
    static DocumentTypeJpaEntity probe(String tenantId, String category, String flow, Boolean active) {
        DocumentTypeJpaEntity entity = new DocumentTypeJpaEntity();
        entity.tenantId = tenantId;
        entity.category = category;
        entity.flow = flow;
        entity.active = active;
        return entity;
    }

    DocumentType toDomain() {
        Set<DocumentFormat> formats = EnumSet.noneOf(DocumentFormat.class);
        DocumentTypeColumnCodec.split(allowedFormats).forEach(key -> DocumentFormat.fromKey(key).ifPresent(formats::add));
        List<String> recent = DocumentTypeColumnCodec.split(recentChangeRequestIds);
        return new DocumentType(id, tenantId, code, category, flow, DocumentTypeColumnCodec.namesFromJson(names), formats,
                maxFileSizeMb == null ? 0 : maxFileSizeMb, Boolean.TRUE.equals(required), expiryYears,
                retentionDays, scanRequired == null || scanRequired, Boolean.TRUE.equals(verificationRequired),
                VerificationMethod.fromStored(verificationMethod), Boolean.TRUE.equals(active), version, createdAt, updatedAt, createdBy, updatedBy, lastChangeRequestId, recent,
                verificationProvider);
    }
}
