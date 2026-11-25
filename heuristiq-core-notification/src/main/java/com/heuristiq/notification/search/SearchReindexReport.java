package com.heuristiq.notification.search;

import java.time.Instant;
import java.util.Map;

/**
 * Progress of a search re-index run: whether it is running, when it started and finished, and for each model how many
 * records were published and how many could not be.
 */
public record SearchReindexReport(State state, Instant startedAt, Instant finishedAt, Map<String, Counts> models, String error) {

    /** Lifecycle of a run. */
    public enum State { IDLE, RUNNING, COMPLETED, FAILED }

    /** Records of one model: published to the search stream, and not published (for example Redis was unreachable). */
    public record Counts(long indexed, long failed) {
    }

    public static SearchReindexReport idle() {
        return new SearchReindexReport(State.IDLE, null, null, Map.of(), null);
    }
}
