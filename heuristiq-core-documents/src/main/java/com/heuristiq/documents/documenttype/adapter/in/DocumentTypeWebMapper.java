package com.railcab.dms.documenttype.adapter.in;

import com.railcab.dms.documenttype.application.DocumentTypeFileCheckService;
import com.railcab.dms.documenttype.application.DocumentTypeSnapshots;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;

/** Maps between the web shapes and the application/domain types. Stateless. {@link #toResponse(DocumentType)} is public so the search projection publishes exactly the DTO the list API returns. */
public final class DocumentTypeWebMapper {

    private DocumentTypeWebMapper() {
    }

    static DocumentTypeFields toFields(DocumentTypeRequest request) {
        return new DocumentTypeFields(request.code(), request.category(), request.flow(), request.names(),
                request.allowedFormats(), request.maxFileSizeMb(), request.required(), request.expiryYears(),
                request.retentionDays(), request.scanRequired(), request.verificationRequired(),
                request.verificationMethod(), request.active(), request.verificationProvider());
    }

    public static DocumentTypeResponse toResponse(DocumentType type) {
        return new DocumentTypeResponse(type.id(), type.tenantId(), type.code(), type.category(), type.flow(), type.names(),
                type.allowedFormats().stream().map(Enum::name).toList(), type.maxFileSizeMb(), type.required(),
                type.expiryYears(), type.retentionDays(), type.scanRequired(), type.verificationRequired(),
                type.verificationMethod().name(), type.active(), type.version(), type.createdAt(), type.updatedAt(), type.createdBy(),
                type.updatedBy(), type.lastChangeRequestId(),
                DocumentTypeSnapshots.summaryOf(type.allowedFormats(), type.maxFileSizeMb(), type.retentionDays()), type.verificationProvider());
    }

    static DocumentTypePageResponse toPage(DocumentTypeRepository.Page page) {
        return new DocumentTypePageResponse(page.content().stream().map(DocumentTypeWebMapper::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    static FileCheckResponse toResponse(DocumentTypeFileCheckService.Result result) {
        return new FileCheckResponse(result.accepted(),
                result.reasons().stream().map(r -> new FileCheckResponse.Reason(r.code(), r.message())).toList(),
                new FileCheckResponse.Rule(result.rule().maxFileSizeMb(), result.rule().allowedFormats(),
                        result.rule().allowedExtensions()));
    }
}
