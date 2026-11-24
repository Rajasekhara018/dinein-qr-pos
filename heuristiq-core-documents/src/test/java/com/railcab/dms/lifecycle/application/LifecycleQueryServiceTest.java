package com.railcab.dms.lifecycle.application;

import com.railcab.dms.common.paging.PagedResult;
import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import com.railcab.dms.document.persistence.DocumentSearchCriteria;
import com.railcab.dms.exception.DmsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The service always scopes to the organization it is given, caps paging, and answers 404 for foreign documents. */
class LifecycleQueryServiceTest {

    private final DocumentMetadataStore store = mock(DocumentMetadataStore.class);
    private final LifecycleQueryService service = new LifecycleQueryService(store);
    private static final DocumentSearchCriteria NONE = new DocumentSearchCriteria(null, null, null, null, null, null, null, null, null);

    @Test
    void searchIsScopedToTheGivenOrganizationOnly() {
        PagedResult<DocumentMetadata> page = new PagedResult<>(List.of(), 0, 20, 0);
        when(store.search(eq("org-a"), any(), anyInt(), anyInt())).thenReturn(page);

        assertSame(page, service.searchDocuments("org-a", NONE, null, null));

        verify(store).search("org-a", NONE, 0, 20);
        verify(store, never()).search(eq("org-b"), any(), anyInt(), anyInt());
    }

    @Test
    void pageSizeIsCappedAtOneHundredAndFloorsAtOne() {
        when(store.search(any(), any(), anyInt(), anyInt())).thenReturn(new PagedResult<>(List.of(), 0, 1, 0));

        service.searchDocuments("org-a", NONE, 3, 100000);
        service.searchDocuments("org-a", NONE, -5, 0);
        service.searchDocuments("org-a", NONE, 1, 100);

        verify(store).search("org-a", NONE, 3, 100);
        verify(store).search("org-a", NONE, 0, 1);
        verify(store).search("org-a", NONE, 1, 100);
    }

    @Test
    void missingOrganizationIsABadRequest() {
        for (String tenant : new String[]{null, "", "  "}) {
            DmsException ex = assertThrows(DmsException.class, () -> service.searchDocuments(tenant, NONE, 0, 10));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(DmsException.class, () -> service.getDocument(tenant, "d")).getStatus());
        }
        verify(store, never()).search(any(), any(), anyInt(), anyInt());
        verify(store, never()).findByTenantIdAndId(any(), any());
    }

    @Test
    void overlongFiltersAndBackwardsRangeAreRejected() {
        String longText = "x".repeat(LifecycleConstants.MAX_QUERY_LENGTH + 1);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(DmsException.class, () -> service.searchDocuments("org-a",
                new DocumentSearchCriteria(longText, null, null, null, null, null, null, null, null), 0, 10)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(DmsException.class, () -> service.searchDocuments("org-a",
                new DocumentSearchCriteria(null, "x".repeat(129), null, null, null, null, null, null, null), 0, 10)).getStatus());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(DmsException.class, () -> service.searchDocuments("org-a",
                new DocumentSearchCriteria(null, null, null, null, null, null, null,
                        Instant.parse("2026-02-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z")), 0, 10)).getStatus());
    }

    @Test
    void detailOfAnotherOrganizationsDocumentIsNotFoundExactlyLikeAMissingOne() {
        when(store.findByTenantIdAndId("org-a", "doc-of-b")).thenReturn(Optional.empty());
        when(store.findByTenantIdAndId("org-a", "missing")).thenReturn(Optional.empty());

        DmsException foreign = assertThrows(DmsException.class, () -> service.getDocument("org-a", "doc-of-b"));
        DmsException missing = assertThrows(DmsException.class, () -> service.getDocument("org-a", "missing"));

        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatus());
        assertEquals(missing.getMessage(), foreign.getMessage());
        verify(store, never()).findByTenantIdAndId(eq("org-b"), any());
    }

    @Test
    void detailIncludesDeletedDocumentsOfTheOrganization() {
        DocumentMetadata deleted = DocumentMetadata.builder().id("d1").tenantId("org-a").deleted(true).build();
        when(store.findByTenantIdAndId("org-a", "d1")).thenReturn(Optional.of(deleted));

        assertSame(deleted, service.getDocument("org-a", "d1"));
    }
}
