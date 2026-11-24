package com.railcab.dms.documenttype.adapter.out.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.documenttype.application.ChangeRequestGateway;
import com.railcab.dms.documenttype.config.DocumentTypeWorkflowProperties;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls {@code POST /api/v1/workflow/change-requests} on Rider_WorkflowService with the caller's own bearer token (so
 * the caller is the maker). The call runs through a {@link ResilientCaller} (timeout, bulkhead, circuit breaker) as
 * {@link Idempotency#UNSAFE}: a submit creates a change request, so it is never retried (a timeout may already have
 * created it). Every failure to submit ends as an APPROVAL_UNAVAILABLE exception (HTTP 503), so nothing is applied
 * without approval. A 4xx answer is a rejection of the request itself and is not counted against the breaker.
 */
@Component
public class WorkflowChangeRequestGateway implements ChangeRequestGateway {
    private static final Logger log = LoggerFactory.getLogger(WorkflowChangeRequestGateway.class);

    private final WebClient client;
    private final ObjectMapper objectMapper;
    private final ResilientCaller caller;
    private final DocumentTypeWorkflowProperties properties;

    public WorkflowChangeRequestGateway(@Qualifier(WorkflowClientConstants.WORKFLOW_WEB_CLIENT_BEAN) WebClient client, ObjectMapper objectMapper,
                                        @Qualifier(WorkflowClientConstants.WORKFLOW_RESILIENT_CALLER_BEAN) ResilientCaller caller,
                                        DocumentTypeWorkflowProperties properties) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.caller = caller;
        this.properties = properties;
    }

    @Override
    public SubmittedChangeRequest submit(Submission s, String bearerToken) {
        String callbackBase = properties.callbackBaseUrl();
        if (!StringUtils.hasText(callbackBase)) {
            throw DocumentTypeException.approvalUnavailable(
                    "The approval callback address is not configured. Nothing was changed.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(DocumentTypeConstants.K_SUBJECT_TYPE, s.subjectType());
        body.put(DocumentTypeConstants.K_SUBJECT_ID, s.subjectId());
        body.put(DocumentTypeConstants.K_TITLE, s.title());
        if (s.summary() != null) {
            body.put(DocumentTypeConstants.K_SUMMARY, s.summary());
        }
        body.put(DocumentTypeConstants.K_BEFORE, s.before());
        body.put(DocumentTypeConstants.K_AFTER, s.after());
        body.put(DocumentTypeConstants.K_CALLBACK_URL,
                callbackBase.trim().replaceAll(DmsCommonConstants.TRAILING_SLASHES_REGEX, "")
                + DocumentTypeConstants.INTERNAL_APPLY_PATH);
        if (s.comment() != null) {
            body.put(DocumentTypeConstants.K_COMMENT, s.comment());
        }
        try {
            Answer answer = post(body, bearerToken);
            if (answer.status() >= WorkflowClientConstants.HTTP_BAD_REQUEST_FLOOR) {
                log.warn("dms.workflow.submit-rejected status={}", answer.status());
                if (answer.status() == WorkflowClientConstants.HTTP_UNAUTHORIZED
                        || answer.status() == WorkflowClientConstants.HTTP_FORBIDDEN) {
                    throw DocumentTypeException.forbidden("The approval service did not accept your session (HTTP "
                            + answer.status() + ")");
                }
                throw DocumentTypeException.invalid("The approval service rejected the change request (HTTP "
                        + answer.status() + ")");
            }
            return parse(answer.body());
        } catch (DocumentTypeException e) {
            throw e;
        } catch (DownstreamUnavailableException e) {
            log.warn("dms.workflow.unavailable reason={}", e.reason());
            throw DocumentTypeException.approvalUnavailable(
                    e.reason() == DownstreamUnavailableException.Reason.CIRCUIT_OPEN
                            ? "The approval service is temporarily unavailable. Nothing was changed."
                            : WorkflowClientConstants.APPROVAL_UNAVAILABLE_MESSAGE);
        } catch (RuntimeException e) {
            log.warn("dms.workflow.submit-failed reason={}", e.getClass().getSimpleName());
            throw DocumentTypeException.approvalUnavailable(WorkflowClientConstants.APPROVAL_UNAVAILABLE_MESSAGE);
        }
    }

    /** HTTP status and body of Workflow's answer (below 500); 5xx, timeouts and connection errors throw. */
    private record Answer(int status, String body) {
    }

    private Answer post(Map<String, Object> body, String bearerToken) {
        WebClient.RequestBodySpec spec = client.post().uri(WorkflowClientConstants.SUBMIT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, DmsCommonConstants.BEARER_PREFIX + bearerToken);
        Answer answer = caller.callBlocking(() -> spec.bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(text -> response.statusCode().value() >= WorkflowClientConstants.HTTP_SERVER_ERROR_FLOOR
                                // 5xx counts against the breaker; surfaced as an error so the caller classifies it.
                                ? Mono.<Answer>error(WebClientResponseException.create(response.statusCode().value(),
                                        "Approval service answered HTTP " + response.statusCode().value(),
                                        response.headers().asHttpHeaders(), text.getBytes(StandardCharsets.UTF_8),
                                        StandardCharsets.UTF_8))
                                : Mono.just(new Answer(response.statusCode().value(), text)))),
                Idempotency.UNSAFE);
        if (answer == null) {
            throw new IllegalStateException("No answer from the approval service");
        }
        return answer;
    }

    private SubmittedChangeRequest parse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode object = root.path(DocumentTypeConstants.K_OBJECT);
            JsonNode request = object.has(DocumentTypeConstants.K_REQUEST) ? object.get(DocumentTypeConstants.K_REQUEST) : object;
            String id = request.path(DocumentTypeConstants.K_ID).asText(null);
            if (id == null || id.isBlank()) {
                throw DocumentTypeException.approvalUnavailable(
                        "The approval service returned no request id. Nothing was changed.");
            }
            return new SubmittedChangeRequest(id, request.path(DocumentTypeConstants.K_STATUS).asText(DocumentTypeConstants.STATUS_PENDING));
        } catch (JsonProcessingException | NullPointerException e) {
            throw DocumentTypeException.approvalUnavailable(
                    "The approval service answer could not be read. Nothing was changed.");
        }
    }
}
