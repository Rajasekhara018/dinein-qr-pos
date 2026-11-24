package com.railcab.dms.search.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.railcab.dms.common.paging.PagedResult;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import com.railcab.dms.lifecycle.application.LifecycleQueryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The two sources publish what the list APIs return, scoped to the organization asked for. */
class ProjectionSourcesTest {

    static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    static final Instant CREATED = Instant.parse("2026-03-04T05:06:07Z");

    static DocumentType type(String id, String tenant, String code, String category, String flow, boolean active) {
        return new DocumentType(id, tenant, code, category, flow, Map.of("en", code), EnumSet.of(DocumentFormat.PDF), 5, true, null, null,
                true, false, VerificationMethod.NONE, active, 1L, CREATED, CREATED, "maker", "maker", "cr-1", List.of("cr-1"));
    }

    static DocumentMetadata document(String id, String tenant) {
        return DocumentMetadata.builder().id(id).tenantId(tenant).category("Customer-Documents").ownerId("owner-1")
                .documentType("PHOTO_ID").originalFileName("Passport.PDF").checksumSha256("ABCDEF123").contentType("application/pdf")
                .sizeBytes(10).status("ACTIVE").scanStatus("CLEAN").deleted(true).createdAt(CREATED).build();
    }

    // ---- document types

    @Test
    void documentTypesCarryTheListDtoAsPayloadAndTheNormalizedSlots() {
        DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
        when(query.list("org-a", null, null, null, 0, 100)).thenReturn(new DocumentTypeRepository.Page(
                List.of(type("t-1", "org-a", "PHOTO_ID", "Customer-Documents", "onboarding", true)), 0, 100, 1, 1));

        ProjectionSource.Page page = new DocumentTypeProjectionSource(query, MAPPER).page("org-a", 0, 100);

        assertTrue(page.last());
        ProjectionRecord record = page.records().get(0);
        assertEquals("t-1", record.modelKey());
        Map<?, ?> payload = (Map<?, ?>) record.fields().get("payload");
        assertEquals("PHOTO_ID", payload.get("code"));
        assertEquals("2026-03-04T05:06:07Z", payload.get("createdAt"), "dates are ISO strings, as in the API response");
        assertEquals("customer-documents", record.fields().get("text1"));
        assertEquals("ONBOARDING", record.fields().get("text2"));
        assertEquals("true", record.fields().get("text3"));
        assertEquals("2026-03-04T05:06:07Z", record.fields().get("createdAt"));
    }

    @Test
    void documentTypesPageUntilTheLastPageAndNeverAskForAnotherOrganization() {
        DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
        List<DocumentType> full = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            full.add(type("t-" + i, "org-a", "C" + i, "docs", null, true));
        }
        when(query.list("org-a", null, null, null, 0, 100)).thenReturn(new DocumentTypeRepository.Page(full, 0, 100, 101, 2));
        when(query.list("org-a", null, null, null, 1, 100)).thenReturn(new DocumentTypeRepository.Page(
                List.of(type("t-100", "org-a", "C100", "docs", null, false)), 1, 100, 101, 2));
        DocumentTypeProjectionSource source = new DocumentTypeProjectionSource(query, MAPPER);

        assertFalse(source.page("org-a", 0, 100).last());
        ProjectionSource.Page last = source.page("org-a", 1, 100);

        assertTrue(last.last());
        assertEquals("false", last.records().get(0).fields().get("text3"));
        verify(query, never()).list(eq("org-b"), any(), any(), any(), any(), any());
    }

    @Test
    void aTypeWithoutFlowHasNoFlowSlot() {
        DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
        when(query.list("org-a", null, null, null, 0, 100)).thenReturn(new DocumentTypeRepository.Page(
                List.of(type("t-1", "org-a", "X", "docs", null, true)), 0, 100, 1, 1));

        ProjectionRecord record = new DocumentTypeProjectionSource(query, MAPPER).page("org-a", 0, 100).records().get(0);

        assertFalse(record.fields().containsKey("text2"));
        assertNull(record.fields().get("text2"));
    }

    // ---- lifecycle documents

    @Test
    void lifecycleDocumentsCarryTheRowAsPayloadAndTheNormalizedSlots() {
        LifecycleQueryService query = mock(LifecycleQueryService.class);
        when(query.searchDocuments(eq("org-a"), any(DocumentSearchCriteria.class), eq(0), eq(100)))
                .thenReturn(new PagedResult<>(List.of(document("d-1", "org-a")), 0, 100, 1));

        ProjectionSource.Page page = new LifecycleProjectionSource(query, MAPPER).page("org-a", 0, 100);

        assertTrue(page.last());
        ProjectionRecord record = page.records().get(0);
        assertEquals("d-1", record.modelKey());
        Map<?, ?> payload = (Map<?, ?>) record.fields().get("payload");
        assertEquals("Passport.PDF", payload.get("originalFileName"));
        assertEquals(true, payload.get("deleted"));
        assertEquals("owner-1", record.fields().get("text1"));
        assertEquals("Customer-Documents", record.fields().get("text2"));
        assertEquals("PHOTO_ID", record.fields().get("text3"));
        assertEquals("active", record.fields().get("text4"));
        assertEquals("CLEAN", record.fields().get("text5"));
        assertEquals("true", record.fields().get("text6"));
        assertEquals("2026-03-04T05:06:07Z", record.fields().get("datetime1"));
        assertEquals("d-1 passport.pdf abcdef123", record.fields().get("text13"));
    }

    @Test
    void lifecycleReadsEverythingOfTheOrganizationDeletedIncludedAndPages() {
        LifecycleQueryService query = mock(LifecycleQueryService.class);
        List<DocumentMetadata> full = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            full.add(document("d-" + i, "org-a"));
        }
        when(query.searchDocuments(eq("org-a"), any(DocumentSearchCriteria.class), eq(0), eq(100)))
                .thenReturn(new PagedResult<>(full, 0, 100, 150));
        when(query.searchDocuments(eq("org-a"), any(DocumentSearchCriteria.class), eq(1), eq(100)))
                .thenReturn(new PagedResult<>(List.of(document("d-100", "org-a")), 1, 100, 150));
        LifecycleProjectionSource source = new LifecycleProjectionSource(query, MAPPER);

        assertFalse(source.page("org-a", 0, 100).last());
        assertTrue(source.page("org-a", 1, 100).last());
        verify(query).searchDocuments(eq("org-a"), eq(new DocumentSearchCriteria(null, null, null, null, null, null, null, null, null)),
                eq(0), eq(100));
        verify(query, never()).searchDocuments(eq("org-b"), any(), any(), any());
    }

    @Test
    void aDocumentWithoutOptionalFieldsOmitsThoseSlotsInsteadOfSendingNulls() {
        LifecycleQueryService query = mock(LifecycleQueryService.class);
        DocumentMetadata bare = DocumentMetadata.builder().id("d-9").tenantId("org-a").build();
        when(query.searchDocuments(eq("org-a"), any(DocumentSearchCriteria.class), eq(0), eq(100)))
                .thenReturn(new PagedResult<>(List.of(bare), 0, 100, 1));

        Map<String, Object> fields = new LifecycleProjectionSource(query, MAPPER).page("org-a", 0, 100).records().get(0).fields();

        assertFalse(fields.containsKey("text1"));
        assertFalse(fields.containsKey("datetime1"));
        assertFalse(fields.containsKey("createdAt"));
        assertEquals("false", fields.get("text6"));
        assertEquals("d-9", fields.get("text13"));
    }
}
