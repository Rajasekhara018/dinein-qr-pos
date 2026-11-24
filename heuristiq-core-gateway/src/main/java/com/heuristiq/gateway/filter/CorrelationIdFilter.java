package com.heuristiq.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Ensures every request carries a {@code X-Correlation-Id} header and emits a structured
 * access log line on completion (method, URI, status, duration ms).
 *
 * <p>Order -90 — runs after the auth filter (-100) so the log can include the authenticated
 * user's ID if already set by that point, but before routing.
 */
@Slf4j
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String START_TIME_ATTR   = CorrelationIdFilter.class.getName() + ".startTime";

    @Override
    public int getOrder() {
        return -90;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        final String finalCorrelationId = correlationId;
        exchange.getAttributes().put(START_TIME_ATTR, System.currentTimeMillis());

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(CORRELATION_HEADER, finalCorrelationId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();

        // Echo correlation ID on the response so clients can match request ↔ log entry
        mutatedExchange.getResponse().getHeaders().set(CORRELATION_HEADER, finalCorrelationId);

        return chain.filter(mutatedExchange)
                .doFinally(signal -> {
                    long duration = System.currentTimeMillis() -
                            (long) exchange.getAttributes().getOrDefault(START_TIME_ATTR, System.currentTimeMillis());
                    int status = exchange.getResponse().getStatusCode() != null
                            ? exchange.getResponse().getStatusCode().value() : 0;
                    log.info("ACCESS correlationId={} method={} uri={} status={} durationMs={}",
                            finalCorrelationId,
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getPath(),
                            status,
                            duration);
                });
    }
}
