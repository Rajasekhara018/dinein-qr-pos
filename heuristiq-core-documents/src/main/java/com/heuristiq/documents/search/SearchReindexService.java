package com.heuristiq.documents.search;

import com.heuristiq.documents.document.domain.DocumentMetadata;
import com.heuristiq.documents.document.persistence.DocumentMetadataStore;
import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.exception.DmsException;
import com.heuristiq.documents.search.projection.WebProjectionPublisher;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Re-publishes every document that is not deleted to the search index, then the organization's two web models
 * ({@code WEB_DMS_DOCUMENT_TYPE}, {@code WEB_DMS_LIFECYCLE_DOCUMENT}) through Rider_SearchAPI's projection ingress.
 * Documents are normally indexed when saved, so any saved before search was running, or while Redis was down, are
 * missing from search until this runs. The web models have no save-time hook: until this runs for an organization
 * their screens fail closed ("has not completed its first backfill"), and they are only as fresh as the last run.
 *
 * <p>One run at a time, in the background, reading in fixed-size pages so memory and the burst of events stay
 * bounded. A document that cannot be published is counted as failed and the run carries on. Deleted documents are
 * not re-published.
 */
@Service
@Slf4j
public class SearchReindexService implements SearchReindexUseCase {

    private final DocumentMetadataStore store;
    private final SearchEventPublisher publisher;
    private final WebProjectionPublisher webProjections;
    private final Clock clock;
    private final int batchSize;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SearchReindexReport> report = new AtomicReference<>(SearchReindexReport.idle());

    @Autowired
    public SearchReindexService(DocumentMetadataStore store, SearchEventPublisher publisher, WebProjectionPublisher webProjections,
                                @Value("${railcab.search.reindex.batch-size:" + SearchConstants.DEFAULT_BATCH_SIZE + "}") int batchSize) {
        this(store, publisher, webProjections, Clock.systemUTC(), batchSize,
                task -> Thread.ofVirtual().name(SearchConstants.REINDEX_THREAD_NAME).start(task));
    }

    SearchReindexService(DocumentMetadataStore store, SearchEventPublisher publisher, WebProjectionPublisher webProjections,
                         Clock clock, int batchSize, Executor executor) {
        this.store = store;
        this.publisher = publisher;
        this.webProjections = webProjections;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, SearchConstants.MAX_BATCH_SIZE));
        this.executor = executor;
    }

    @Override
    public StartOutput start(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw DmsException.badRequest(SearchConstants.NO_ORGANIZATION_MESSAGE);
        }
        if (!running.compareAndSet(false, true)) {
            return new StartOutput(false, report.get());
        }
        report.set(new SearchReindexReport(SearchReindexReport.State.RUNNING, clock.instant(), null, Map.of(), null));
        String correlationId = MDC.get(DmsCommonConstants.MDC_KEY);
        try {
            executor.execute(() -> run(tenantId, correlationId));
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

    private void run(String tenantId, String correlationId) {
        if (correlationId != null) {
            MDC.put(DmsCommonConstants.MDC_KEY, correlationId);
        }
        Instant started = report.get().startedAt();
        Map<String, SearchReindexReport.Counts> models = new LinkedHashMap<>();
        long indexed = 0;
        long failed = 0;
        try {
            int pageNumber = 0;
            List<DocumentMetadata> page;
            do {
                page = store.findActivePage(pageNumber++, batchSize);
                for (DocumentMetadata document : page) {
                    if (publisher.publishIndex(document.getSearchModel(), document.getSearchModelKey(), document.getSearchIndexFields())) {
                        indexed++;
                    } else {
                        failed++;
                    }
                }
                models.put(SearchConstants.MODEL_DOCUMENT, new SearchReindexReport.Counts(indexed, failed));
                publish(SearchReindexReport.State.RUNNING, started, null, models, null);
            } while (page.size() == batchSize);
            log.info("Search re-index of documents completed: {} indexed, {} failed", indexed, failed);
            String problems = publishWebModels(tenantId, started, models);
            publish(problems.isEmpty() ? SearchReindexReport.State.COMPLETED : SearchReindexReport.State.FAILED, started,
                    clock.instant(), models, problems.isEmpty() ? null : problems);
        } catch (RuntimeException e) {
            log.error("Search re-index failed after {} indexed, {} failed: {}", indexed, failed, e.getMessage(), e);
            publish(SearchReindexReport.State.FAILED, started, clock.instant(), models, e.getClass().getSimpleName());
        } finally {
            MDC.remove(DmsCommonConstants.MDC_KEY);
            running.set(false);
        }
    }

    /** Publishes each web model of the organization; returns what went wrong per model, or an empty text. */
    private String publishWebModels(String tenantId, Instant started, Map<String, SearchReindexReport.Counts> models) {
        StringJoiner problems = new StringJoiner("; ");
        for (String model : webProjections.models()) {
            WebProjectionPublisher.Outcome outcome = webProjections.publish(model, tenantId, (done, lost) -> {
                models.put(model, new SearchReindexReport.Counts(done, lost));
                publish(SearchReindexReport.State.RUNNING, started, null, models, null);
            });
            models.put(model, new SearchReindexReport.Counts(outcome.indexed(), outcome.failed()));
            if (outcome.status() != WebProjectionPublisher.Status.PUBLISHED) {
                problems.add(model + ": " + outcome.message());
            }
        }
        return problems.toString();
    }

    private void publish(SearchReindexReport.State state, Instant started, Instant finished,
                         Map<String, SearchReindexReport.Counts> models, String error) {
        report.set(new SearchReindexReport(state, started, finished, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(models)), error));
    }
}
