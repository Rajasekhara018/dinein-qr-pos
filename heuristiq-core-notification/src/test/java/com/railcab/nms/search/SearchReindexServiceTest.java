package com.railcab.nms.search;

import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.domain.repository.PageQuery;
import com.railcab.nms.domain.repository.PageResult;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchReindexServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);

    private ITemplateRepository templates;
    private SearchEventPublisher publisher;

    @BeforeEach
    void setUp() {
        templates = mock(ITemplateRepository.class);
        publisher = mock(SearchEventPublisher.class);
    }

    private SearchReindexService service(int batchSize, java.util.concurrent.Executor executor) {
        return new SearchReindexService(templates, publisher, CLOCK, batchSize, executor);
    }

    private static NotificationTemplate template(String id) {
        NotificationTemplate template = mock(NotificationTemplate.class);
        when(template.getSearchModel()).thenReturn("NOTIFICATION_TEMPLATE");
        when(template.getSearchModelKey()).thenReturn(id);
        when(template.getSearchIndexFields()).thenReturn(Map.of("text1", id));
        return template;
    }

    /** Serves {@code all} in pages of the requested size. */
    private void templatesAre(List<NotificationTemplate> all) {
        when(templates.findByActiveTrue(any(PageQuery.class))).thenAnswer(call -> {
            PageQuery query = call.getArgument(0);
            int from = query.page() * query.size();
            int to = Math.min(from + query.size(), all.size());
            return new PageResult<>(from >= all.size() ? List.of() : all.subList(from, to), query.page(), query.size(), all.size());
        });
    }

    @Test
    void publishesEveryActiveTemplateAcrossPagesAndCountsThem() {
        templatesAre(List.of(template("t1"), template("t2"), template("t3")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);

        SearchReindexService service = service(2, Runnable::run);
        SearchReindexUseCase.StartOutput out = service.start();

        assertTrue(out.started());
        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(3, 0), service.status().models().get("NOTIFICATION_TEMPLATE"));
        assertEquals(CLOCK.instant(), service.status().startedAt());
        verify(publisher, times(3)).publishIndex(eq("NOTIFICATION_TEMPLATE"), any(), anyMap());
        verify(templates, times(2)).findByActiveTrue(any(PageQuery.class));
    }

    @Test
    void anEmptyCatalogCompletesWithZeroCounts() {
        templatesAre(List.of());

        SearchReindexService service = service(10, Runnable::run);
        service.start();

        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertEquals(new SearchReindexReport.Counts(0, 0), service.status().models().get("NOTIFICATION_TEMPLATE"));
    }

    @Test
    void aTemplateThatCannotBePublishedIsCountedAsFailedAndTheRunCarriesOn() {
        templatesAre(List.of(template("t1"), template("t2")));
        when(publisher.publishIndex(any(), eq("t1"), anyMap())).thenReturn(false);
        when(publisher.publishIndex(any(), eq("t2"), anyMap())).thenReturn(true);

        SearchReindexService service = service(10, Runnable::run);
        service.start();

        assertEquals(new SearchReindexReport.Counts(1, 1), service.status().models().get("NOTIFICATION_TEMPLATE"));
    }

    @Test
    void onlyOneRunAtATime() {
        templatesAre(List.of(template("t1")));
        when(publisher.publishIndex(any(), any(), anyMap())).thenReturn(true);
        List<Runnable> queued = new ArrayList<>();

        SearchReindexService service = service(10, queued::add);
        SearchReindexUseCase.StartOutput first = service.start();
        SearchReindexUseCase.StartOutput second = service.start();

        assertTrue(first.started());
        assertFalse(second.started());
        assertEquals(SearchReindexReport.State.RUNNING, second.report().state());
        assertEquals(1, queued.size());

        queued.get(0).run();
        assertEquals(SearchReindexReport.State.COMPLETED, service.status().state());
        assertTrue(service.start().started(), "a new run may start once the last finished");
    }

    @Test
    void aReadFailureMarksTheRunFailedAndFreesItForARetry() {
        when(templates.findByActiveTrue(any(PageQuery.class))).thenThrow(new IllegalStateException("database down"));

        SearchReindexService service = service(10, Runnable::run);
        service.start();

        assertEquals(SearchReindexReport.State.FAILED, service.status().state());
        assertEquals("IllegalStateException", service.status().error());
        assertTrue(service.start().started());
    }
}
