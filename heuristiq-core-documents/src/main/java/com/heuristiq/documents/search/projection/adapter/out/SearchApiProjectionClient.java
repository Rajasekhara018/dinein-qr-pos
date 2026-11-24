package com.railcab.dms.search.projection.adapter.out;

import com.railcab.dms.common.DmsCommonConstants;
import com.railcab.dms.search.projection.ProjectionConstants;
import com.railcab.dms.search.projection.ProjectionGateway;
import com.railcab.dms.search.projection.ProjectionPhase;
import com.railcab.dms.search.projection.ProjectionRecord;
import com.railcab.dms.search.projection.config.SearchProjectionProperties;
import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls {@code POST /api/v1/search/internal/projections} on Rider_SearchAPI with the service token, through the shared
 * {@link ResilientCaller} (timeout, bulkhead, circuit breaker). Every step is safe to repeat (BEGIN with the same
 * generation, a re-sent batch, a guarded COMPLETE, a plain flag), so the call is {@link Idempotency#SAFE} and a
 * transient failure is retried. A 409 is the lease held by another run; any other refusal or outage is an
 * {@link ProjectionGateway.UnavailableException} whose message holds no secret.
 */
@Component
public class SearchApiProjectionClient implements ProjectionGateway {

    private final WebClient client;
    private final ResilientCaller caller;
    private final SearchProjectionProperties properties;

    public SearchApiProjectionClient(@Qualifier(ProjectionConstants.WEB_CLIENT_BEAN) WebClient client,
                                     @Qualifier(ProjectionConstants.RESILIENT_CALLER_BEAN) ResilientCaller caller,
                                     SearchProjectionProperties properties) {
        this.client = client;
        this.caller = caller;
        this.properties = properties;
    }

    @Override
    public void send(ProjectionPhase phase, String model, String tenantId, String generation, List<ProjectionRecord> records) {
        if (!StringUtils.hasText(properties.serviceToken())) {
            throw new UnavailableException(ProjectionConstants.MESSAGE_NOT_CONFIGURED, null);
        }
        Map<String, Object> body = body(phase, model, tenantId, generation, records);
        int status;
        try {
            Integer answered = caller.callBlocking(() -> client.post().uri(ProjectionConstants.INGRESS_PATH)
                    .header(DmsCommonConstants.SERVICE_TOKEN_HEADER, properties.serviceToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(text -> response.statusCode().value() >= ProjectionConstants.HTTP_SERVER_ERROR_FLOOR
                                    ? Mono.<Integer>error(WebClientResponseException.create(response.statusCode().value(),
                                            "Search API answered HTTP " + response.statusCode().value(),
                                            response.headers().asHttpHeaders(), text.getBytes(StandardCharsets.UTF_8),
                                            StandardCharsets.UTF_8))
                                    : Mono.just(response.statusCode().value()))),
                    Idempotency.SAFE);
            status = answered == null ? ProjectionConstants.HTTP_SERVER_ERROR_FLOOR : answered;
        } catch (DownstreamUnavailableException e) {
            throw new UnavailableException("the search service is unavailable (" + e.reason() + ")", e);
        } catch (RuntimeException e) {
            throw new UnavailableException("the search service could not be reached (" + e.getClass().getSimpleName() + ")", e);
        }
        if (status == ProjectionConstants.HTTP_CONFLICT) {
            throw new LeaseHeldException(ProjectionConstants.MESSAGE_IN_PROGRESS);
        }
        if (status >= ProjectionConstants.HTTP_CLIENT_ERROR_FLOOR) {
            throw new UnavailableException("the search service refused the " + phase + " step (HTTP " + status + ")", null);
        }
    }

    /** The ingress body of one step; the organization is the caller's, passed in by the application layer. */
    static Map<String, Object> body(ProjectionPhase phase, String model, String tenantId, String generation,
                                    List<ProjectionRecord> records) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ProjectionConstants.BODY_PHASE, phase.name());
        body.put(ProjectionConstants.BODY_MODEL, model);
        body.put(ProjectionConstants.BODY_TENANT_ID, tenantId);
        body.put(ProjectionConstants.BODY_PARTITION, ProjectionConstants.PARTITION);
        body.put(ProjectionConstants.BODY_GENERATION, generation);
        List<Map<String, Object>> rows = new ArrayList<>(records.size());
        for (ProjectionRecord record : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(ProjectionConstants.BODY_MODEL_KEY, record.modelKey());
            row.put(ProjectionConstants.BODY_FIELDS, record.fields());
            rows.add(row);
        }
        body.put(ProjectionConstants.BODY_RECORDS, rows);
        return body;
    }
}
