package com.heuristiq.uam.orgstructure.domain;

/**
 * Limits of the bulk import, supplied from configuration at wiring time so the application layer
 * does not depend on the config layer.
 *
 * @param maxRows   most data rows one file may hold
 * @param chunkSize rows applied per transaction
 */
public record ImportLimits(int maxRows, int chunkSize) {
}
