package com.railcab.dms.documenttype.adapter.out.mongo;

import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * Mongo shape of a {@link DocumentType}. Numeric and boolean fields are boxed on purpose so a query-by-example probe can
 * leave them unset. Indexes: code is unique per organization; category and flow are filterable per organization.
 */
@Document(collection = "dms_document_types")
@CompoundIndexes({
        @CompoundIndex(name = "uq_tenant_code", def = "{'tenantId': 1, 'code': 1}", unique = true),
        @CompoundIndex(name = "idx_tenant_category", def = "{'tenantId': 1, 'category': 1}"),
        @CompoundIndex(name = "idx_tenant_flow", def = "{'tenantId': 1, 'flow': 1}")
})
class DocumentTypeMongoDocument {

    @Id
    String id;
    String tenantId;
    String code;
    String category;
    String flow;
    LinkedHashMap<String, String> names;
    List<String> allowedFormats;
    Integer maxFileSizeMb;
    Boolean required;
    Integer expiryYears;
    /** Null on documents stored before the field existed: read as "service default". */
    Integer retentionDays;
    /** Null on documents stored before the field existed: read as true (scan required). */
    Boolean scanRequired;
    /** Missing on documents stored before the field existed: read as false (no verification). */
    Boolean verificationRequired;
    /** Missing (or unknown) on documents stored before the field existed: read as NONE. */
    String verificationMethod;
    String verificationProvider;
    Boolean active;
    @Version
    Long version;
    Instant createdAt;
    Instant updatedAt;
    String createdBy;
    String updatedBy;
    String lastChangeRequestId;
    List<String> recentChangeRequestIds;

    static DocumentTypeMongoDocument fromDomain(DocumentType type) {
        DocumentTypeMongoDocument doc = new DocumentTypeMongoDocument();
        doc.id = type.id();
        doc.tenantId = type.tenantId();
        doc.code = type.code();
        doc.category = type.category();
        doc.flow = type.flow();
        doc.names = new LinkedHashMap<>(type.names());
        doc.allowedFormats = type.allowedFormats().stream().map(Enum::name).toList();
        doc.maxFileSizeMb = type.maxFileSizeMb();
        doc.required = type.required();
        doc.expiryYears = type.expiryYears();
        doc.retentionDays = type.retentionDays();
        doc.scanRequired = type.scanRequired();
        doc.verificationRequired = type.verificationRequired();
        doc.verificationMethod = type.verificationMethod().name();
        doc.verificationProvider = type.verificationProvider();
        doc.active = type.active();
        doc.version = type.version();
        doc.createdAt = type.createdAt();
        doc.updatedAt = type.updatedAt();
        doc.createdBy = type.createdBy();
        doc.updatedBy = type.updatedBy();
        doc.lastChangeRequestId = type.lastChangeRequestId();
        doc.recentChangeRequestIds = new ArrayList<>(type.recentChangeRequestIds());
        return doc;
    }

    /** A probe for query-by-example: only the organization and the optional filters are set. */
    static DocumentTypeMongoDocument probe(String tenantId, String category, String flow, Boolean active) {
        DocumentTypeMongoDocument doc = new DocumentTypeMongoDocument();
        doc.tenantId = tenantId;
        doc.category = category;
        doc.flow = flow;
        doc.active = active;
        return doc;
    }

    DocumentType toDomain() {
        Set<DocumentFormat> formats = EnumSet.noneOf(DocumentFormat.class);
        if (allowedFormats != null) {
            allowedFormats.forEach(key -> DocumentFormat.fromKey(key).ifPresent(formats::add));
        }
        return new DocumentType(id, tenantId, code, category, flow,
                names == null ? new LinkedHashMap<>() : new LinkedHashMap<>(names), formats,
                maxFileSizeMb == null ? 0 : maxFileSizeMb, Boolean.TRUE.equals(required), expiryYears,
                retentionDays, scanRequired == null || scanRequired, Boolean.TRUE.equals(verificationRequired),
                VerificationMethod.fromStored(verificationMethod), Boolean.TRUE.equals(active), version, createdAt, updatedAt, createdBy, updatedBy, lastChangeRequestId,
                recentChangeRequestIds == null ? List.of() : List.copyOf(recentChangeRequestIds),
                verificationProvider);
    }
}
