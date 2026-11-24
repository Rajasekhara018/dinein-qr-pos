package com.heuristiq.notification.search;

import com.heuristiq.notification.domain.model.NotificationDomainConstants;

import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.repository.ITemplateRepository;
import com.heuristiq.notification.domain.repository.PageQuery;
import com.heuristiq.notification.domain.repository.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Re-publishes every active notification template to the search index. Templates are normally indexed when saved, so
 * any saved before search was running, or while Redis was down, are missing from search until this runs.
 *
 * <p>One run at a time, in the background, reading in fixed-size pages so memory and the burst of events stay
 * bounded. A template that cannot be published is counted as failed and the run carries on. Inactive (soft-deleted)
 * templates are not re-published.
 */
@Service
@Slf4j
public class SearchReindexService implements SearchReindexUseCase {

    private final ITemplateRepository templates;
    private final SearchEventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SearchReindexReport> report = new AtomicReference<>(SearchReindexReport.idle());

    @Autowired
    public SearchReindexService(ITemplateRepository templates, SearchEventPublisher publisher,
                                @Value("${railcab.search.reindex.batch-size:" + SearchConstants.DEFAULT_REINDEX_BATCH_SIZE + "}") int batchSize) {
        this(templates, publisher, Clock.systemUTC(), batchSize, task -> Thread.ofVirtual().name(SearchConstants.REINDEX_THREAD_NAME).start(task));
    }

    SearchReindexService(ITemplateRepository templates, SearchEventPublisher publisher, Clock clock, int batchSize,
                         Executor executor) {
        this.templates = templates;
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, SearchConstants.MAX_REINDEX_BATCH_SIZE));
        this.executor = executor;
    }

    @Override
    public StartOutput start() {
        if (!running.compareAndSet(false, true)) {
            return new StartOutput(false, report.get());
        }
        report.set(new SearchReindexReport(SearchReindexReport.State.RUNNING, clock.instant(), null, Map.of(), null));
        try {
            executor.execute(this::run);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return new StartOutput(true, report.get());
    }

    @Override
    public SearchReindexReport status() {
        return report.get();
    }

    private void run() {
        Instant started = report.get().startedAt();
        long indexed = 0;
        long failed = 0;
        try {
            int pageNumber = 0;
            PageResult<NotificationTemplate> page;
            do {
                page = templates.findByActiveTrue(new PageQuery(pageNumber++, batchSize));
                for (NotificationTemplate template : page.content()) {
                    if (publisher.publishIndex(template.getSearchModel(), template.getSearchModelKey(), template.getSearchIndexFields())) {
                        indexed++;
                    } else {
                        failed++;
                    }
                }
                report.set(new SearchReindexReport(SearchReindexReport.State.RUNNING, started, null,
                        Map.of(NotificationDomainConstants.TEMPLATE_SEARCH_MODEL, new SearchReindexReport.Counts(indexed, failed)), null));
            } while ((long) pageNumber * batchSize < page.totalElements());
            report.set(new SearchReindexReport(SearchReindexReport.State.COMPLETED, started, clock.instant(),
                    Map.of(NotificationDomainConstants.TEMPLATE_SEARCH_MODEL, new SearchReindexReport.Counts(indexed, failed)), null));
            log.info("Search re-index completed: {} indexed, {} failed", indexed, failed);
        } catch (RuntimeException e) {
            log.error("Search re-index failed after {} indexed, {} failed: {}", indexed, failed, e.getMessage(), e);
            report.set(new SearchReindexReport(SearchReindexReport.State.FAILED, started, clock.instant(),
                    Map.of(NotificationDomainConstants.TEMPLATE_SEARCH_MODEL, new SearchReindexReport.Counts(indexed, failed)), e.getClass().getSimpleName()));
        } finally {
            running.set(false);
        }
    }
}
