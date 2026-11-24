package com.heuristiq.uam.readiness.adapter.out;

import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.resilient.Idempotency;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.uam.readiness.domain.ReadinessConstants;
import com.heuristiq.uam.readiness.domain.ReadinessSource;
import com.heuristiq.uam.readiness.domain.ServiceReadinessPort;
import com.heuristiq.uam.readiness.domain.ServiceReport;

/**
 * Calls each service's internal readiness endpoint ({@code GET <base><path>?applicationCode=&tenantId=}) through its own
 * {@link ResilientCaller} (timeout, bulkhead, circuit breaker, at most one retry: the call is a read and safe to repeat).
 * Sends the service credential the service expects and the caller's correlation id. Outcomes:
 * <ul>
 *   <li>200 with a readable report: ANSWERED;</li>
 *   <li>200 with anything else: MALFORMED;</li>
 *   <li>4xx: REFUSED ({@code reason=HTTP_<status>}), a configuration problem (credential, path);</li>
 *   <li>timeout, connection error, 5xx, circuit open, bulkhead full: UNAVAILABLE ({@code reason} = the resilience reason).</li>
 * </ul>
 */
public class HttpServiceReadinessAdapter implements ServiceReadinessPort {

    private static final Logger log = LoggerFactory.getLogger(HttpServiceReadinessAdapter.class);

    private final Map<ReadinessSource, Endpoint> endpoints;
    private final String serviceToken;
    private final ServiceJwtMinter jwtMinter;

    /**
     * @param endpoints    one endpoint per service
     * @param serviceToken the shared {@code X-Rider-Service-Token} value; blank means none is sent (the service will refuse)
     * @param jwtMinter    mints the SERVICE bearer token for {@link ReadinessAuthMode#SERVICE_JWT} services
     */
    public HttpServiceReadinessAdapter(Map<ReadinessSource, Endpoint> endpoints, String serviceToken, ServiceJwtMinter jwtMinter) {
        this.endpoints = new EnumMap<>(endpoints);
        this.serviceToken = serviceToken;
        this.jwtMinter = jwtMinter;
    }

    @Override
    public ServiceReport fetch(ReadinessSource source, String applicationCode, String tenantId) {
        Endpoint endpoint = endpoints.get(source);
        if (endpoint == null) {
            log.warn("No readiness endpoint configured for {}", source.slug());
            return ServiceReport.unavailable(source, ReadinessConstants.REASON_ERROR);
        }
        String correlationId = MDC.get(ReadinessAdapterConstants.CORRELATION_ID_MDC_KEY);
        String body;
        try {
            body = endpoint.caller().callBlocking(() -> endpoint.client().get()
                    .uri(builder -> builder.path(endpoint.path())
                            .queryParam(ReadinessAdapterConstants.PARAM_APPLICATION_CODE, applicationCode)
                            .queryParam(ReadinessAdapterConstants.PARAM_TENANT_ID, tenantId)
                            .build())
                    .headers(headers -> addHeaders(headers, endpoint.auth(), correlationId))
                    .retrieve()
                    .bodyToMono(String.class)
                    .defaultIfEmpty(""), Idempotency.SAFE);
        } catch (WebClientResponseException e) {
            log.warn("Readiness endpoint of {} refused the call: HTTP {}", source.slug(), e.getStatusCode().value());
            return ServiceReport.refused(source, ReadinessConstants.REASON_HTTP_PREFIX + e.getStatusCode().value());
        } catch (DownstreamUnavailableException e) {
            log.warn("Readiness endpoint of {} unavailable: {}", source.slug(), e.reason());
            return ServiceReport.unavailable(source, e.reason().name());
        } catch (RuntimeException e) {
            // Boundary of an outbound call: any other failure means no answer, never a pass.
            log.warn("Readiness call to {} failed: {}", source.slug(), e.toString());
            return ServiceReport.unavailable(source, ReadinessConstants.REASON_ERROR);
        }
        return ReadinessReportParser.parse(body)
                .map(checks -> ServiceReport.answered(source, checks))
                .orElseGet(() -> {
                    log.warn("Readiness endpoint of {} answered with something that is not a readiness report", source.slug());
                    return ServiceReport.malformed(source);
                });
    }

    private void addHeaders(HttpHeaders headers, ReadinessAuthMode auth, String correlationId) {
        if (StringUtils.hasText(correlationId)) {
            headers.set(ReadinessAdapterConstants.CORRELATION_ID_HEADER, correlationId);
        }
        if (auth == ReadinessAuthMode.SERVICE_JWT) {
            headers.set(HttpHeaders.AUTHORIZATION, ReadinessAdapterConstants.BEARER_PREFIX + jwtMinter.mint());
        } else if (StringUtils.hasText(serviceToken)) {
            headers.set(ReadinessAdapterConstants.SERVICE_TOKEN_HEADER, serviceToken);
        }
    }

    /**
     * One service's readiness endpoint.
     *
     * @param path   path of the readiness endpoint, e.g. {@code /api/v1/wallet/internal/readiness}
     * @param auth   which credential it expects
     * @param client WebClient whose base URL is the service root
     * @param caller the service's own resilient caller
     */
    public record Endpoint(String path, ReadinessAuthMode auth, WebClient client, ResilientCaller caller) {
    }
}
