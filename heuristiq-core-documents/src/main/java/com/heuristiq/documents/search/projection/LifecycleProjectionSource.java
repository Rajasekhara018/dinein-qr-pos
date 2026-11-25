package com.heuristiq.documents.search.projection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.documents.common.paging.PagedResult;
import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentSearchCriteria;
import com.heuristiq.documents.lifecycle.adapter.in.LifecycleDocumentRow;
import com.heuristiq.documents.lifecycle.adapter.in.LifecycleMapper;
import com.heuristiq.documents.lifecycle.application.LifecycleQueryService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The organization's documents, deleted ones included, for {@code WEB_DMS_LIFECYCLE_DOCUMENT}: the same rows, in the
 * same order (newest first), as {@code GET /api/v1/dms/lifecycle/documents}. Slots: text1 ownerId, text2 category,
 * text3 documentType, text4 status (lower), text5 scanStatus, text6 deleted, datetime1 createdAt, and text13 the
 * lower-cased id, file name and checksum that the screen's free-text box searches.
 */
@Component
public class LifecycleProjectionSource implements ProjectionSource {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final DocumentSearchCriteria EVERYTHING =
            new DocumentSearchCriteria(null, null, null, null, null, null, null, null, null);

    private final LifecycleQueryService queryService;
    private final ObjectMapper mapper;

    public LifecycleProjectionSource(LifecycleQueryService queryService, ObjectMapper mapper) {
        this.queryService = queryService;
        this.mapper = mapper;
    }

    @Override
    public String model() {
        return ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT;
    }

    @Override
    public Page page(String tenantId, int pageIndex, int size) {
        PagedResult<DocumentMetadata> page = queryService.searchDocuments(tenantId, EVERYTHING, pageIndex, size);
        List<ProjectionRecord> records = new ArrayList<>(page.content().size());
        for (DocumentMetadata document : page.content()) {
            LifecycleDocumentRow row = LifecycleMapper.toRow(document);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put(ProjectionConstants.FIELD_PAYLOAD, mapper.convertValue(row, MAP));
            ProjectionSlots.put(fields, ProjectionConstants.FIELD_CREATED_AT, ProjectionSlots.instant(row.createdAt()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT1, ProjectionSlots.text(row.ownerId()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT2, ProjectionSlots.text(row.category()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT3, ProjectionSlots.text(row.documentType()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT4, ProjectionSlots.lower(row.status()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT5, ProjectionSlots.text(row.scanStatus()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_TEXT6, String.valueOf(row.deleted()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_DATETIME1, ProjectionSlots.instant(row.createdAt()));
            ProjectionSlots.put(fields, ProjectionConstants.SLOT_QUERY_TEXT,
                    ProjectionSlots.searchText(row.id(), row.originalFileName(), document.getChecksumSha256()));
            records.add(new ProjectionRecord(row.id(), fields));
        }
        return new ProjectionSource.Page(records, page.content().size() < size || pageIndex + 1 >= page.totalPages());
    }
}
