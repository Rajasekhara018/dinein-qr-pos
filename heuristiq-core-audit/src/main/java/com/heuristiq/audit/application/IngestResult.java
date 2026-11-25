package com.heuristiq.audit.application;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Outcome of one ingest call. Valid events are stored even when others in the batch are rejected.
 *
 * @param received   events in the request
 * @param accepted   newly stored
 * @param duplicates valid but already stored (same eventId), safely ignored
 * @param rejected   failed validation; see {@code rejections}
 * @param dropped    valid but dropped by the per-user rate limit (screen views only)
 * @param rejections index and reason of each rejected event
 */
@Schema(description = "Result of an ingest call.")
public record IngestResult(int received, int accepted, int duplicates, int rejected, int dropped,
                           List<Rejection> rejections) {

    /** One rejected event. */
    public record Rejection(int index, String reason) {
    }
}
