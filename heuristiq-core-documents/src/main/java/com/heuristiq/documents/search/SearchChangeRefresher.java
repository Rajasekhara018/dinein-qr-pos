package com.heuristiq.documents.search;

import com.heuristiq.documents.search.projection.ProjectionConstants;
import com.heuristiq.documents.search.projection.WebProjectionPublisher;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends a DMS web list (document types, lifecycle documents) to search again shortly after a change to it, so the screens show
 * new, changed and deleted rows within seconds instead of after the next re-index. Each send replaces the organization's whole
 * list. Changes close together are sent once; a send that fails is tried again a few times.
 */
@Component
public class SearchChangeRefresher {

    private static final Logger log = LoggerFactory.getLogger(SearchChangeRefresher.class);
    private static final String BASE = "/api/v1/dms";

    private final WebProjectionPublisher publisher;
    private final long delayMs;
    private final long retryMs;
    private final int maxAttempts;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "dms-search-changes");
        thread.setDaemon(true);
        return thread;
    });
    /** model|tenant -> the attempt the pending send is on. */
    private final Map<String, Integer> pending = new LinkedHashMap<>();
    private boolean drainScheduled;

    public SearchChangeRefresher(WebProjectionPublisher publisher,
                                 @Value("${railcab.dms.search.change-delay-ms:2000}") long delayMs,
                                 @Value("${railcab.dms.search.change-retry-ms:30000}") long retryMs,
                                 @Value("${railcab.dms.search.change-max-attempts:5}") int maxAttempts) {
        this.publisher = publisher;
        this.delayMs = delayMs;
        this.retryMs = retryMs;
        this.maxAttempts = maxAttempts;
    }

    /** The web lists a successful change to {@code path} affects; empty for a path that touches none. */
    public static List<String> affected(String path) {
        if (path == null) {
            return List.of();
        }
        if (path.startsWith(BASE + "/document-types") || path.startsWith(BASE + "/internal/document-types")) {
            return List.of(ProjectionConstants.MODEL_DOCUMENT_TYPE);
        }
        if (path.startsWith(BASE + "/documents")) {
            return List.of(ProjectionConstants.MODEL_LIFECYCLE_DOCUMENT);
        }
        return List.of();
    }

    /** Asks for {@code models} to be sent again for {@code tenantId}; nothing for a caller without an organization. */
    public void changed(List<String> models, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return;
        }
        for (String model : models) {
            if (publisher.models().contains(model)) {
                enqueue(model + "|" + tenantId, 1, delayMs);
            }
        }
    }

    private void enqueue(String key, int attempt, long afterMs) {
        synchronized (pending) {
            pending.merge(key, attempt, Math::min);
            if (drainScheduled) {
                return;
            }
            drainScheduled = true;
        }
        scheduler.schedule(this::drain, afterMs, TimeUnit.MILLISECONDS);
    }

    /** Sends every list changed since the last drain; one that fails is tried again later. */
    void drain() {
        List<Map.Entry<String, Integer>> work;
        synchronized (pending) {
            work = new ArrayList<>(pending.entrySet());
            pending.clear();
            drainScheduled = false;
        }
        for (Map.Entry<String, Integer> item : work) {
            String key = item.getKey();
            int at = key.indexOf('|');
            String model = key.substring(0, at);
            String tenantId = key.substring(at + 1);
            int attempt = item.getValue();
            WebProjectionPublisher.Outcome outcome;
            try {
                outcome = publisher.publish(model, tenantId, (indexed, failed) -> { });
            } catch (RuntimeException e) {
                outcome = new WebProjectionPublisher.Outcome(WebProjectionPublisher.Status.FAILED, 0, 0, e.getMessage());
            }
            if (outcome.status() == WebProjectionPublisher.Status.PUBLISHED) {
                log.info("dms.search.changed model={} tenant={} rows={}", model, tenantId, outcome.indexed());
            } else if (attempt < maxAttempts) {
                log.warn("dms.search.changed.retry model={} tenant={} attempt={} reason={}", model, tenantId, attempt, outcome.message());
                scheduler.schedule(() -> enqueue(key, attempt + 1, delayMs), retryMs, TimeUnit.MILLISECONDS);
            } else {
                log.warn("dms.search.changed.gave-up model={} tenant={} reason={} (run the re-index to catch up)", model, tenantId,
                        outcome.message());
            }
        }
    }

    @PreDestroy
    void stop() {
        scheduler.shutdownNow();
    }
}
