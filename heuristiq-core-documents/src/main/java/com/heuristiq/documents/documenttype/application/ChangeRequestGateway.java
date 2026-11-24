package com.railcab.dms.documenttype.application;

import java.util.Map;

/**
 * Port to the maker-checker engine (Rider_WorkflowService change requests). An implementation must never swallow a
 * failure: when the request could not be submitted it throws a {@code DocumentTypeException} of kind
 * APPROVAL_UNAVAILABLE, so nothing is changed without approval.
 */
public interface ChangeRequestGateway {

    /**
     * Submits a change request on behalf of the caller. The implementation adds the address Workflow calls back.
     *
     * @param submission  what to submit
     * @param bearerToken the caller's own token (without the "Bearer " prefix)
     * @return the created request
     */
    SubmittedChangeRequest submit(Submission submission, String bearerToken);

    /**
     * What is sent to Workflow.
     *
     * @param before the current state, exactly as this service hashes it on apply
     * @param after  the proposed state
     */
    record Submission(String subjectType, String subjectId, String title, String summary, Map<String, Object> before,
                      Map<String, Object> after, String comment) {
    }

    /** The request Workflow created. */
    record SubmittedChangeRequest(String id, String status) {
    }
}
