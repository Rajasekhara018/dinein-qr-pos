package com.heuristiq.uam.search;

import java.util.Optional;

/** Re-publishes existing identity records to the search index, for data saved before search was running. */
public interface SearchReindexServiceUseCase {

    record StartInput(Optional<String> model) {}
    record StartOutput(boolean started, SearchReindexReport report) {}

    /**
     * Starts a background re-index of every indexed model, or of just {@code model} when given. Only one run at a
     * time: when one is already running nothing new starts and {@code started} is false.
     *
     * @throws IllegalArgumentException if {@code model} is not an indexed model
     */
    StartOutput start(StartInput input);

    /** The current or last run's progress. */
    SearchReindexReport status();
}
