package com.heuristiq.documents.documenttype.application;

/**
 * Answer to a submitted create or update: nothing is stored until a checker approves.
 *
 * @param changeRequestId the Workflow change request
 * @param status          its status, normally {@code PENDING}
 */
public record SubmissionResult(String changeRequestId, String status) {
}
