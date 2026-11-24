package com.heuristiq.search.service;

/** Thrown when Elasticsearch calls exhaust their resilience4j retry/circuit-breaker budget. */
public class SearchIndexUnavailableException extends RuntimeException {
    public SearchIndexUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
