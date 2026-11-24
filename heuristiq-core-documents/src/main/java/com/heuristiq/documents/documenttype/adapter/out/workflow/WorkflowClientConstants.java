package com.heuristiq.documents.documenttype.adapter.out.workflow;

import java.time.Duration;

/**
 * Wire and wiring values of the client toward Rider_WorkflowService: the submit path, the HTTP status bounds it
 * classifies, bean names and the resilience settings of the submit call.
 */
public final class WorkflowClientConstants {

    private WorkflowClientConstants() {
    }

    /** Workflow's endpoint that creates a change request. */
    public static final String SUBMIT_PATH = "/api/v1/workflow/change-requests";

    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_BAD_REQUEST_FLOOR = 400;
    public static final int HTTP_SERVER_ERROR_FLOOR = 500;

    // ---- wiring
    public static final String WORKFLOW_DOWNSTREAM = "workflow-service";
    public static final String WORKFLOW_WEB_CLIENT_BEAN = "workflowWebClient";
    public static final String WORKFLOW_RESILIENT_CALLER_BEAN = "workflowResilientCaller";
    public static final int WORKFLOW_MAX_CONCURRENT_CALLS = 10;
    public static final int BREAKER_WINDOW = 10;
    public static final int BREAKER_MIN_CALLS = 5;
    public static final float BREAKER_FAILURE_PERCENT = 50f;
    public static final Duration BREAKER_OPEN = Duration.ofSeconds(20);
    /** Floor applied to the configured call timeout. */
    public static final long MIN_TIMEOUT_MS = 200;

    // ---- messages
    /** Answer when the submit failed for a reason other than a rejection. */
    public static final String APPROVAL_UNAVAILABLE_MESSAGE = "The approval service is unavailable. Nothing was changed.";
}
