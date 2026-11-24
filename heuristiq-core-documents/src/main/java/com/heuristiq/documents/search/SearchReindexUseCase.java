package com.railcab.dms.search;

/**
 * Re-publishes existing documents to the search index, for data saved before search was running, and backfills the
 * organization's web models (document types, lifecycle documents) through Rider_SearchAPI's projection ingress.
 */
public interface SearchReindexUseCase {

    record StartOutput(boolean started, SearchReindexReport report) {}

    /**
     * Starts a background re-index of the active documents and of the organization's web models. Only one run at a
     * time: when one is already running nothing new starts and {@code started} is false.
     *
     * @param tenantId the organization of the caller's session (never a request value); the web models are published for it
     */
    StartOutput start(String tenantId);

    /** The current or last run's progress. */
    SearchReindexReport status();
}
