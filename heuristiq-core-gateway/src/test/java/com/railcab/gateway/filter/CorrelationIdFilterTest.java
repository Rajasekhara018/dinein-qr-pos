package com.heuristiq.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
    }

    @Test
    void request_withoutCorrelationId_generatesOne() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/users").build());

        GatewayFilterChain chain = ex -> {
            // Correlation ID must be present on the mutated downstream request
            String id = ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_HEADER);
            assertNotNull(id, "X-Correlation-Id should be generated");
            assertFalse(id.isBlank());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void request_withExistingCorrelationId_preservesIt() {
        String existingId = "my-trace-id-12345";
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/users")
                        .header(CorrelationIdFilter.CORRELATION_HEADER, existingId)
                        .build());

        GatewayFilterChain chain = ex -> {
            String id = ex.getRequest().getHeaders().getFirst(CorrelationIdFilter.CORRELATION_HEADER);
            assertEquals(existingId, id);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    }

    @Test
    void response_hasCorrelationIdHeader() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/admin/v1/users").build());

        GatewayFilterChain chain = ex -> Mono.empty();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String responseId = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdFilter.CORRELATION_HEADER);
        assertNotNull(responseId, "X-Correlation-Id must be echoed on response");
        assertFalse(responseId.isBlank());
    }

    @Test
    void order_isMinus90() {
        assertEquals(-90, filter.getOrder());
    }
}
