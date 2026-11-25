package com.heuristiq.documents.search.projection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the web models DMS owns to Rider_SearchAPI for one organization, as one leased generation per model:
 * BEGIN, the rows in batches of {@link ProjectionConstants#BATCH_SIZE}, COMPLETE (which removes what was not re-sent),
 * then TENANT_COMPLETE (which marks the model ready for that organization).
 *
 * <p>COMPLETE is only sent after every row was accepted: a gap would otherwise delete the rows that were not sent.
 * After any failure nothing is completed, so the index keeps what it had. Memory stays bounded to one page.
 */
@Service
public class WebProjectionPublisher {
    private static final Logger log = LoggerFactory.getLogger(WebProjectionPublisher.class);

    /** How a model's publish ended. */
    public enum Status { PUBLISHED, IN_PROGRESS, FAILED }

    /** Rows accepted by the ingress, rows that were not, and why the run stopped (null when published). */
    public record Outcome(Status status, long indexed, long failed, String message) {
    }

    private final Map<String, ProjectionSource> sources = new LinkedHashMap<>();
    private final ProjectionGateway gateway;
    private final Clock clock;

    public WebProjectionPublisher(List<ProjectionSource> sources, ProjectionGateway gateway, Clock clock) {
        sources.forEach(source -> this.sources.put(source.model(), source));
        this.gateway = gateway;
        this.clock = clock;
    }

    /** The models this service publishes, in publish order. */
    public List<String> models() {
        return List.copyOf(sources.keySet());
    }

    /**
     * Publishes one model of the organization. Never throws for an expected failure; the outcome says what happened.
     *
     * @param tenantId the organization of the caller's session (never a request value)
     */
    public Outcome publish(String model, String tenantId, ProgressListener progress) {
        ProjectionSource source = sources.get(model);
        if (source == null) {
            throw new IllegalArgumentException("Unknown projection model " + model);
        }
        String generation = ProjectionConstants.GENERATION_PREFIX + Long.toString(clock.millis(), Character.MAX_RADIX);
        long indexed = 0;
        long failed = 0;
        try {
            gateway.send(ProjectionPhase.BEGIN, model, tenantId, generation, List.of());
            long position = 0;
            int pageIndex = 0;
            ProjectionSource.Page page;
            do {
                page = source.page(tenantId, pageIndex++, ProjectionConstants.BATCH_SIZE);
                List<ProjectionRecord> batch = new ArrayList<>(page.records().size());
                for (ProjectionRecord record : page.records()) {
                    Map<String, Object> fields = new LinkedHashMap<>(record.fields());
                    fields.put(ProjectionConstants.SLOT_POSITION, position++);
                    batch.add(new ProjectionRecord(record.modelKey(), fields));
                }
                if (!batch.isEmpty()) {
                    try {
                        gateway.send(ProjectionPhase.BATCH, model, tenantId, generation, batch);
                    } catch (ProjectionGateway.UnavailableException e) {
                        failed += batch.size();
                        throw e;
                    }
                    indexed += batch.size();
                    progress.update(indexed, failed);
                }
            } while (!page.last());
            gateway.send(ProjectionPhase.COMPLETE, model, tenantId, generation, List.of());
            gateway.send(ProjectionPhase.TENANT_COMPLETE, model, tenantId, generation, List.of());
            return new Outcome(Status.PUBLISHED, indexed, failed, null);
        } catch (ProjectionGateway.LeaseHeldException e) {
            log.warn("dms.search.projection.in-progress model={}", model);
            return new Outcome(Status.IN_PROGRESS, indexed, failed, ProjectionConstants.MESSAGE_IN_PROGRESS);
        } catch (ProjectionGateway.UnavailableException e) {
            log.warn("dms.search.projection.unavailable model={} indexed={} reason={}", model, indexed, e.getMessage());
            return new Outcome(Status.FAILED, indexed, failed, e.getMessage());
        } catch (RuntimeException e) {
            log.error("dms.search.projection.failed model={} indexed={}", model, indexed, e);
            return new Outcome(Status.FAILED, indexed, failed, e.getClass().getSimpleName());
        }
    }

    /** Receives the running counts after each accepted batch, so the progress endpoint moves. */
    @FunctionalInterface
    public interface ProgressListener {
        void update(long indexed, long failed);
    }
}
