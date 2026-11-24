package com.heuristiq.notification.search;

/** Re-publishes existing notification templates to the search index, for data saved before search was running. */
public interface SearchReindexUseCase {

    record StartOutput(boolean started, SearchReindexReport report) {}

    /**
     * Starts a background re-index of the active notification templates. Only one run at a time: when one is already
     * running nothing new starts and {@code started} is false.
     */
    StartOutput start();

    /** The current or last run's progress. */
    SearchReindexReport status();
}
