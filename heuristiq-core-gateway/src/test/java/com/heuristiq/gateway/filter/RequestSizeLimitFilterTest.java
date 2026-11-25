package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RequestSizeLimitFilterTest {

    private static final long MAX_BYTES = 1024L; // 1 KB for tests

    private RequestSizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties();
        props.getRequest().setMaxBodySizeBytes(MAX_BYTES);
        filter = new RequestSizeLimitFilter(props, new ObjectMapper());
    }

    @Test
    void requestUnderLimit_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/bookings/v1/rides")
                        .contentLength(512)
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get(), "Chain must be called for under-limit request");
    }

    @Test
    void requestOverLimit_returns413() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/bookings/v1/rides")
                        .contentLength(MAX_BYTES + 1)
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertFalse(chainCalled.get(), "Chain must NOT be called when request is too large");
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exchange.getResponse().getStatusCode());
    }

    @Test
    void requestWithNoContentLength_passesThrough() {
        // Content-Length = -1 means absent (chunked / streaming)
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/bookings/v1/rides").build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get(), "Chain must be called when Content-Length is absent");
    }

    @Test
    void exactlyAtLimit_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/bookings/v1/rides")
                        .contentLength(MAX_BYTES)
                        .build());

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertTrue(chainCalled.get());
    }

    @Test
    void order_isMinus95() {
        assertEquals(-95, filter.getOrder());
    }
}
