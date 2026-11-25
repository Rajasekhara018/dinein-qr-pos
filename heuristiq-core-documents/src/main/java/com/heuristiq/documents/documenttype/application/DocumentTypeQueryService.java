package com.heuristiq.documents.documenttype.application;

import com.heuristiq.documents.documenttype.domain.DefaultDocumentTypes;
import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeConstants;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Read side of document types, always within one organization, and the rule lookup uploads use. */
@Service
public class DocumentTypeQueryService implements DocumentTypeRules {

    private final DocumentTypeRepository repository;
    private final BlockedFileTypes blocked;
    private final DefaultDocumentTypeService defaultType;

    /** Without the built-in default type (nothing is created and no fallback applies). */
    public DocumentTypeQueryService(DocumentTypeRepository repository, BlockedFileTypes blocked) {
        this(repository, blocked, DefaultDocumentTypeService.disabled());
    }

    @Autowired
    public DocumentTypeQueryService(DocumentTypeRepository repository, BlockedFileTypes blocked,
                                    DefaultDocumentTypeService defaultType) {
        this.repository = repository;
        this.blocked = blocked;
        this.defaultType = defaultType;
    }

    /**
     * Lists the organization's types, sorted by code.
     *
     * @param category optional filter (case-insensitive); blank means any
     * @param flow     optional filter; blank means any
     * @param active   optional filter; null means any
     * @param page     zero-based page
     * @param size     page size, capped at {@link DocumentTypeConstants#MAX_PAGE_SIZE}
     */
    public DocumentTypeRepository.Page list(String tenantId, String category, String flow, Boolean active, Integer page,
                                            Integer size) {
        requireTenant(tenantId);
        defaultType.ensureOnce(tenantId);
        int safePage = page == null ? 0 : Math.max(page, 0);
        int safeSize = size == null ? DocumentTypeConstants.DEFAULT_PAGE_SIZE
                : Math.min(Math.max(size, 1), DocumentTypeConstants.MAX_PAGE_SIZE);
        String safeCategory = StringUtils.hasText(category) ? category.trim().toLowerCase(Locale.ROOT) : null;
        String safeFlow = StringUtils.hasText(flow) ? flow.trim().toUpperCase(Locale.ROOT) : null;
        return repository.list(tenantId, new DocumentTypeRepository.Query(safeCategory, safeFlow, active, safePage, safeSize));
    }

    /** One type of the organization; another organization's id is a plain NOT_FOUND. */
    public DocumentType get(String tenantId, String id) {
        requireTenant(tenantId);
        return repository.findById(tenantId, id).orElseThrow(() -> DocumentTypeException.notFound("Document type not found"));
    }

    /**
     * Resolves the rule an upload of the named type must satisfy, in this order:
     * <ol>
     *   <li>The organization has the named type and it is active: its rule is used, and the caller's category must be the
     *       type's category (the {@code DEFAULT} type fits every category).</li>
     *   <li>The named type exists but is inactive, or belongs to another category: rejected. A type that was switched off
     *       on purpose never falls back to something more permissive.</li>
     *   <li>The organization has no type of that code at all: the organization's active {@code DEFAULT} type is used.</li>
     *   <li>No such type and no active {@code DEFAULT} (it was deactivated, or the feature is off): rejected, as before.</li>
     * </ol>
     * The blocked extensions and content types and the content sniff apply on top of whichever type is used.
     */
    @Override
    public DocumentTypeRule resolveRule(String tenantId, String category, String documentTypeCode) {
        if (!StringUtils.hasText(tenantId)) {
            throw DocumentTypeException.invalid("A document type needs an organization in the session");
        }
        if (!StringUtils.hasText(documentTypeCode)) {
            throw DocumentTypeException.invalid("documentType is required");
        }
        String code = documentTypeCode.trim().toUpperCase(Locale.ROOT);
        defaultType.ensureOnce(tenantId);
        Optional<DocumentType> named = repository.findByCode(tenantId, code);
        DocumentType type;
        if (named.isPresent()) {
            type = named.filter(DocumentType::active)
                    .orElseThrow(() -> DocumentTypeException.invalid("Unknown or inactive document type " + code));
            boolean anyCategory = DefaultDocumentTypes.isDefaultCode(code);
            if (!anyCategory && StringUtils.hasText(category) && !type.category().equals(category.trim().toLowerCase(Locale.ROOT))) {
                throw DocumentTypeException.invalid("Document type " + code + " does not belong to category " + category.trim());
            }
        } else {
            type = repository.findByCode(tenantId, DocumentTypeConstants.DEFAULT_TYPE_CODE)
                    .filter(DocumentType::active)
                    .filter(found -> defaultType.enabled())
                    .orElseThrow(() -> DocumentTypeException.invalid("Unknown or inactive document type " + code));
        }
        Set<String> extensions = new LinkedHashSet<>();
        Set<String> contentTypes = new LinkedHashSet<>();
        for (DocumentFormat format : type.allowedFormats()) {
            extensions.addAll(format.extensions());
            contentTypes.addAll(format.contentTypes());
        }
        extensions.removeIf(ext -> blocked.extensions().stream().anyMatch(b -> b.equalsIgnoreCase(ext)));
        contentTypes.removeIf(ct -> blocked.contentTypes().stream().anyMatch(b -> b.equalsIgnoreCase(ct)));
        List<String> formats = type.allowedFormats().stream().map(Enum::name).toList();
        return new DocumentTypeRule(type.code(), type.category(), type.maxFileSizeMb(), formats,
                List.copyOf(extensions), List.copyOf(contentTypes), type.retentionDays(), type.scanRequired(),
                type.verificationRequired(), type.verificationMethod(), type.verificationProvider());
    }

    private static void requireTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw DocumentTypeException.invalid("Your session has no organization");
        }
    }
}
