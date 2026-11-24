package com.railcab.dms.search;

import com.railcab.dms.document.domain.DocumentMetadata;
import com.railcab.dms.document.persistence.DocumentMetadataStore;
import com.railcab.dms.exception.DmsException;
import com.railcab.dms.search.projection.WebProjectionPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchReindexServiceTest {

    private static final String ORG = "org-a";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);

    private DocumentMetadataStore store;
    private SearchEventPublisher publisher;
    private WebProjectionPublisher web;

    @BeforeEach
    void setUp() {
        store = mock(DocumentMetadataStore.class);
        publisher = mock(SearchEventPublisher.class);
        web = mock(WebProjectionPublisher.class);
        when(web.models()).thenReturn(List.of("WEB_A", "WEB_B"));
        when(web.publish(any(), any(), any())).thenReturn(new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.PUBLISHED, 0, 0, null));
    }

    private SearchReindexService service(int batchSize, java.util.concurrent.Executor executor) {
        return new SearchReindexService(store, publisher, web, CLOCK, batchSize, executor);
    }

    private static DocumentMetadata template(String id) {
        DocumentMetadata template = mock(DocumentMetadata.class);
        when(template.getSearchModel()).thenReturn("DOCUMENT");
        when(template.getSearchModelKey()).thenReturn(id);
        when(template.getSearchIndexFields()).thenReturn(Map.of("text1", id));
        return template;
    }

    /** Serves {@code all} in pages of the requested size. */
    private void templatesAre(List<DocumentMetadata> all) {
        when(store.findActivePage(anyInt(), anyInt())).thenAnswer(call -> {
            int page = call.getArgument(0);
            int size = call.getArgument(1);
            int from = page * size;
            int to = Math.min(from + size, all.size());
            return from >= all.size() ? List.<DocumentMetadata>of() : all.subList(from, to);
        });
    }

    @Test
    void publishesEveryDocumentAcrossPagesAndCountsThem() {
        templatesAre(List.of(template("t1"), template("t2"), template("t3")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);

        SearchReindexService service = service(2, Runnable::run);
        SearchReindexUseCase.StartOutput out = service.start(ORG);

        assertTrue(out.started());
        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(3, 0), service.status().models().get("DOCUMENT"));
        assertEquals(CLOCK.instant(), service.status().startedAt());
        verify(publisher, times(3)).publishIndex(eq("DOCUMENT"), any(), anyMap());
        verify(store, times(2)).findActivePage(anyInt(), anyInt());
    }

    @Test
    void noDocumentsCompletesWithZeroCounts() {
        templatesAre(List.of());

        SearchReindexService service = service(10, Runnable::run);
        service.start(ORG);

        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(0, 0), service.status().models().get("DOCUMENT"));
    }

    @Test
    void aDocumentThatCannotBePublishedIsCountedAsFailedAndTheRunCarriesOn() {
        templatesAre(List.of(template("t1"), template("t2")));
        when(publisher.publishIndex(any(), eq("t1"), anyMap())).thenReturn(false);
        when(publisher.publishIndex(any(), eq("t2"), anyMap())).thenReturn(true);

        SearchReindexService service = service(10, Runnable::run);
        service.start(ORG);

        assertEquals(new SearchReindexReport.Counts(1, 1), service.status().models().get("DOCUMENT"));
    }

    @Test
    void onlyOneRunAtATime() {
        templatesAre(List.of(template("t1")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);
        List<Runnable> queued = new ArrayList<>();

        SearchReindexService service = service(10, queued::add);
        SearchReindexUseCase.StartOutput first = service.start(ORG);
        SearchReindexUseCase.StartOutput second = service.start(ORG);

        assertTrue(first.started());
        assertFalse(second.started());
        assertEquals(SearchReindexReport.State.RUNNING, second.report().state());
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertTrue(service.start(ORG).started(), "a new run may start once the last finished");
    }

    @Test
    void aReadFailureMarksTheRunFailedAndFreesItForARetry() {
        when(store.findActivePage(anyInt(), anyInt())).thenThrow(new IllegalStateException("database down"));

        SearchReindexService service = service(10, Runnable::run);
        service.start(ORG);

        assertEquals(SearchReindexReport.State.FAILED, service.status().state());
        assertEquals("IllegalStateException", service.status().error());
        assertTrue(service.start(ORG).started());
    }

    @Test
    void afterTheDocumentsItPublishesEachWebModelForTheCallersOrganizationOnly() {
        templatesAre(List.of());
        when(web.publish(eq("WEB_A"), eq(ORG), any())).thenReturn(new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.PUBLISHED, 7, 0, null));
        when(web.publish(eq("WEB_B"), eq(ORG), any())).thenReturn(new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.PUBLISHED, 3, 0, null));

        SearchReindexService service = service(10, Runnable::run);
        service.start(ORG);

        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(7, 0), service.status().models().get("WEB_A"));
        assertEquals(new SearchReindexReport.Counts(3, 0), service.status().models().get("WEB_B"));
        assertEquals(new SearchReindexReport.Counts(0, 0), service.status().models().get("DOCUMENT"));
        verify(web, times(2)).publish(any(), eq(ORG), any());
    }

    @Test
    void aWebModelThatCouldNotBePublishedMakesTheRunFailedAndNamesTheModelWhileTheOtherStillRuns() {
        templatesAre(List.of());
        when(web.publish(eq("WEB_A"), eq(ORG), any())).thenReturn(new WebProjectionPublisher.Outcome(
                WebProjectionPublisher.Status.IN_PROGRESS, 0, 0, "another projection run is in progress"));

        SearchReindexService service = service(10, Runnable::run);
        service.start(ORG);

        assertEquals(SearchReindexReport.State.FAILED, service.status().state());
        assertEquals("WEB_A: another projection run is in progress", service.status().error());
        verify(web).publish(eq("WEB_B"), eq(ORG), any());
        assertTrue(service.start(ORG).started(), "a failed run frees the slot");
    }

    @Test
    void aDocumentReadFailureSkipsTheWebModels() {
        when(store.findActivePage(anyInt(), anyInt())).thenThrow(new IllegalStateException("database down"));

        service(10, Runnable::run).start(ORG);

        verify(web, times(0)).publish(any(), any(), any());
    }

    @Test
    void aSessionWithoutAnOrganizationIsRefusedAndNothingStarts() {
        SearchReindexService service = service(10, Runnable::run);

        assertThrows(DmsException.class, () -> service.start(" "));
        assertThrows(DmsException.class, () -> service.start(null));
        assertEquals(SearchReindexReport.State.IDLE, service.status().state());
    }
}
