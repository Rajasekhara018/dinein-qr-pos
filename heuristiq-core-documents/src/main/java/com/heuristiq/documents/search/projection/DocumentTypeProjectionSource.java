package com.heuristiq.documents.search.projection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.documents.documenttype.adapter.in.DocumentTypeResponse;
import com.heuristiq.documents.documenttype.adapter.in.DocumentTypeWebMapper;
import com.heuristiq.documents.documenttype.application.DocumentTypeQueryService;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The organization's document types for {@code WEB_DMS_DOCUMENT_TYPE}: the same rows, in the same order (by code), as
 * {@code GET /api/v1/dms/document-types}. Slots: text1 category (lower), text2 flow (upper), text3 active.
 */
@Component
public class DocumentTypeProjectionSource implements ProjectionSource {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final DocumentTypeQueryService queryService;
    private final ObjectMapper mapper;

    public DocumentTypeProjectionSource(DocumentTypeQueryService queryService, ObjectMapper mapper) {
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @Override
    public String model() {
        return ProjectionConstants.MODEL_DOCUMENT_TYPE;
    }

    @Override
    public Page page(String tenantId, int pageIndex, int size) {
        DocumentTypeRepository.Page page = queryService.list(tenantId, null, null, null, pageIndex, size);
        List<ProjectionRecord> records = new ArrayList<>(page.content().size());
        page.content().forEach(type -> {
            DocumentTypeResponse dto = DocumentTypeWebMapper.toResponse(type);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put(ProjectionConstants.FIELD_PAYLOAD, mapper.convertValue(dto, MAP));
            ProjectionSlots.put(fields, ProjectionConstants.FIELD_CREATED_AT, ProjectionSlots.instant(dto.createdAt()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT1, ProjectionSlots.lower(dto.category()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT2, ProjectionSlots.upper(dto.flow()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT3, String.valueOf(dto.active()));
            records.add(new ProjectionRecord(dto.id(), fields));
        });
        return new Page(records, page.content().size() < size || pageIndex + 1 >= page.totalPages());
    }
}
