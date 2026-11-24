package com.heuristiq.resilient;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Forwards the organization of the inbound request's session (from MDC, set from the verified token) as
 * {@code X-Tenant-Id} on every outbound WebClient call, so a sibling service that is called with the backend service
 * token still limits what it reads and writes to that organization.
 *
 * <p>Like {@link CorrelationIdPropagation}, this jar is shared by Spring Boot 3 and Boot 4 services, so it only calls
 * {@code HttpHeaders} methods that exist in both ({@code getFirst(String)}).
 */
public final class TenantIdPropagation {

    public static final String HEADER = "X-Tenant-Id";
    public static final String MDC_KEY = "tenantId";

    private TenantIdPropagation() { }

    public static ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String tenantId = MDC.get(MDC_KEY);
            if (tenantId == null || tenantId.isBlank() || request.headers().getFirst(HEADER) != null) {
                return Mono.just(request);
            }
            return Mono.just(ClientRequest.from(request).header(HEADER, tenantId).build());
        });
    }
}
