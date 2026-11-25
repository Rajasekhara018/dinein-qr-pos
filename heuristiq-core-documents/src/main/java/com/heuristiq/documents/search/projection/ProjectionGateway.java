package com.heuristiq.documents.search.projection;

import java.util.List;

/** Port toward Rider_SearchAPI's projection ingress. */
public interface ProjectionGateway {

    /**
     * Sends one step of a generation for one model of one organization.
     *
     * @param records only for {@link ProjectionPhase#BATCH}; empty for the other phases
     * @throws LeaseHeldException   another generation of the same model and organization holds the lease
     * @throws UnavailableException the ingress could not be reached, refused the call, or is not configured
     */
    void send(ProjectionPhase phase, String model, String tenantId, String generation, List<ProjectionRecord> records);

    /** Another run holds the lease (HTTP 409). Expected, so it carries no stack trace. */
    class LeaseHeldException extends RuntimeException {
        public LeaseHeldException(String message) {
            super(message, null, false, false);
        }
    }

    /** The ingress could not take the step. The message never contains secrets. */
    class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
