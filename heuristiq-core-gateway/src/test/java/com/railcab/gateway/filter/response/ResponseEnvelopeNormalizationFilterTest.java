package com.heuristiq.gateway.filter.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResponseEnvelopeNormalizationFilter — the reactive glue around ResponseEnvelopeNormalizer
 * (whose transformation logic itself is covered directly in ResponseEnvelopeNormalizerTest).
 * Proves: disabled by default means a completely untouched pass-through (the safety property this
 * filter's dormant-by-default design depends on), enabling it rewrites a JSON body, and a non-JSON
 * content type is left alone even when enabled.
 */
class ResponseEnvelopeNormalizationFilterTest {

    private ObjectMapper objectMapper;
    private ResponseEnvelopeNormalizer normalizer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        normalizer = new ResponseEnvelopeNormalizer(objectMapper);
    }

    private ResponseEnvelopeNormalizationFilter filterWith(boolean enabled) {
        ResponseNormalizationProperties properties = new ResponseNormalizationProperties();
        properties.setEnabled(enabled);
        return new ResponseEnvelopeNormalizationFilter(normalizer, properties);
    }

    @Test
    void disabledByDefault_leavesResponseCompletelyUnchanged() {
        ResponseEnvelopeNormalizationFilter filter = filterWith(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/uam/identity/auth/login").build());

        String originalBody = """
                {"status":true,"object":{"rideId":"r1"},"errorMsg":null}""";

        GatewayFilterChain chain = ex -> {
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap(originalBody.getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        String actual = capturedBody(exchange);
        assertEquals(originalBody, actual);
    }

    @Test
    void enabled_rewritesJsonBodyIntoCanonicalEnvelope() {
        ResponseEnvelopeNormalizationFilter filter = filterWith(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/rider/ride/rides/r1").build());
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        String originalBody = """
                {"status":true,"object":{"rideId":"r1"},"errorMsg":null}""";

        GatewayFilterChain chain = ex -> {
            ex.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap(originalBody.getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        JsonNode result = parseCapturedBody(exchange);
        assertTrue(result.get("success").asBoolean());
        assertEquals("r1", result.get("data").get("rideId").asText());
    }

    @Test
    void enabled_nonJsonContentType_leftUnchanged() {
        ResponseEnvelopeNormalizationFilter filter = filterWith(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/uam/actuator/health").build());

        String originalBody = "OK";

        GatewayFilterChain chain = ex -> {
            ex.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            DataBuffer buffer = ex.getResponse().bufferFactory().wrap(originalBody.getBytes(StandardCharsets.UTF_8));
            return ex.getResponse().writeWith(Mono.just(buffer));
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(originalBody, capturedBody(exchange));
    }

    @Test
    void order_is100() {
        assertEquals(100, filterWith(true).getOrder());
    }

    private String capturedBody(MockServerWebExchange exchange) {
        Flux<DataBuffer> body = exchange.getResponse().getBody().cast(DataBuffer.class);
        DataBuffer joined = DataBufferUtils.join(body).block();
        byte[] bytes = new byte[joined.readableByteCount()];
        joined.read(bytes);
        DataBufferUtils.release(joined);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private JsonNode parseCapturedBody(MockServerWebExchange exchange) {
        try {
            return objectMapper.readTree(capturedBody(exchange));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
