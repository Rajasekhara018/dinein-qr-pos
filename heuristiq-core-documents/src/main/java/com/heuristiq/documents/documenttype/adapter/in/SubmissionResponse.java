package com.heuristiq.documents.documenttype.adapter.in;

/** Answer to a create or update proposal: the approval request that now waits for a checker. */
public record SubmissionResponse(String changeRequestId, String status) {
}
