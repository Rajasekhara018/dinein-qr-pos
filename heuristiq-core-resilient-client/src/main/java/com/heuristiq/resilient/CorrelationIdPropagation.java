package com.heuristiq.resilient;

import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

/**
 * Forwards the inbound request's correlation id (from MDC) on every outbound WebClient call.
 *
 * <p>This jar is shared by Spring Boot 3 (Framework 6) and Boot 4 (Framework 7) services, so it may only call
 * {@code HttpHeaders} methods that exist, with the same signature, in both. {@code containsKey(Object)} is NOT one of
 * them (Framework 7's {@code HttpHeaders} no longer implements {@code MultiValueMap}), which crashed every outbound
 * call in the Boot 4 services with a {@code NoSuchMethodError}; {@code getFirst(String)} exists in both.
 */
public final class CorrelationIdPropagation {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdPropagation() { }

    public static ExchangeFilterFunction filter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String id = MDC.get(MDC_KEY);
            if (id == null || request.headers().getFirst(HEADER) != null) {
                return Mono.just(request);
            }
            return Mono.just(ClientRequest.from(request).header(HEADER, id).build());
        });
    }
}
