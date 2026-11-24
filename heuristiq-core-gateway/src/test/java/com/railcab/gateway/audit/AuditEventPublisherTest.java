package com.heuristiq.gateway.audit;

import com.heuristiq.gateway.common.constants.GatewayConstants;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventPublisherTest {

    private static final DefaultDataBufferFactory FACTORY = new DefaultDataBufferFactory();

    private final AuditProperties props = new AuditProperties();
    private final AuditPathRules rules = new AuditPathRules(props);
    private final AuditEventFactory factory = new AuditEventFactory(props, rules);
    private final AuditEventQueue queue = new AuditEventQueue(100, Long.MAX_VALUE);
    private final AuditBatcher batcher = new AuditBatcher(queue, batch -> Mono.empty(), new AuditEventRedactor(props, rules),
            props, System::currentTimeMillis);
    private final AuditEventPublisher publisher = new AuditEventPublisher(props, rules, factory, queue, batcher, java.time.Clock.systemUTC());

    private static DataBuffer buffer(String text) {
        return FACTORY.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, String body) {
        MockServerHttpRequest.BodyBuilder b = MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ID, "u-42").header(GatewayConstants.X_USER_ROLE, "ADMIN")
                .header(GatewayConstants.X_USER_ROLES, "ADMIN,SUPPORT").header("X-Correlation-Id", "corr-7")
                .header("User-Agent", "Mozilla/5.0").header("Authorization", "Bearer secret.jwt.value")
                .remoteAddress(new InetSocketAddress("203.0.113.9", 4000));
        if (body == null) {
            return MockServerWebExchange.from(b.build());
        }
        return MockServerWebExchange.from(b.header("Content-Type", "application/json").body(Flux.just(buffer(body))));
    }

    @Test
    void recordsOneEventAfterTheChainFinishesAndLeavesTheRequestAlone() {
        MockServerWebExchange ex = exchange(HttpMethod.GET, "/api/rider/config/config/settings/42", null);
        ex.getAttributes().put(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(9, 3));
        ex.getAttributes().put(AuditExchangeAttributes.MODULE, "CONFIGURATION");
        ex.getAttributes().put(AuditExchangeAttributes.SERVICE, "rider-config");

        StepVerifier.create(publisher.filter(ex, e -> {
            e.getResponse().setStatusCode(HttpStatus.OK);
            assertThat(queue.size()).as("event is recorded after the chain, not before").isZero();
            return Mono.empty();
        })).verifyComplete();

        assertThat(queue.size()).isEqualTo(1);
        GatewayAuditEvent e = queue.drain(1).get(0);
        assertThat(e.getEventType()).isEqualTo("ACCESS");
        assertThat(e.getUserId()).isEqualTo("u-42");
        assertThat(e.getRole()).isEqualTo("ADMIN");
        assertThat(e.getRoles()).containsExactly("ADMIN", "SUPPORT");
        assertThat(e.getUserType()).isEqualTo("STAFF");
        assertThat(e.getPrivilegeIds()).containsExactly(3, 9);
        assertThat(e.getFeature()).isEqualTo("rider-config");
        assertThat(e.getModule()).isEqualTo("CONFIGURATION");
        assertThat(e.getMethod()).isEqualTo("GET");
        assertThat(e.getPath()).isEqualTo("/api/rider/config/config/settings/{id}");
        assertThat(e.getStatusCode()).isEqualTo(200);
        assertThat(e.getOutcome()).isEqualTo("SUCCESS");
        assertThat(e.getCorrelationId()).isEqualTo("corr-7");
        assertThat(e.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(e.getAppChannel()).isEqualTo("ADMIN_WEB");
        assertThat(e.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(e.getEventId()).hasSize(36);
        assertThat(e.getDetails()).isNull();
    }

    @Test
    void deniedRequestsAreRecordedWithTheRequiredPrivileges() {
        MockServerWebExchange ex = exchange(HttpMethod.POST, "/api/rider/config/config/settings", null);
        ex.getAttributes().put(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(5));
        ex.getAttributes().put(AuditExchangeAttributes.DENIED, Boolean.TRUE);
        StepVerifier.create(publisher.filter(ex, e -> {
            e.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return e.getResponse().setComplete();
        })).verifyComplete();
        GatewayAuditEvent e = queue.drain(1).get(0);
        assertThat(e.getOutcome()).isEqualTo("DENIED");
        assertThat(e.getStatusCode()).isEqualTo(403);
        assertThat(e.getPrivilegeIds()).containsExactly(5);
        assertThat(e.getDetails()).containsEntry("denyReason", "PRIVILEGE");
    }

    @Test
    void failuresAndMissingStatusAreRecordedAsFailure() {
        MockServerWebExchange ex = exchange(HttpMethod.GET, "/api/uam/users", null);
        StepVerifier.create(publisher.filter(ex, e -> Mono.error(new IllegalStateException("boom"))))
                .expectErrorMessage("boom").verify();
        GatewayAuditEvent e = queue.drain(1).get(0);
        assertThat(e.getStatusCode()).isEqualTo(500);
        assertThat(e.getOutcome()).isEqualTo("FAILURE");
    }

    @Test
    void skippedPathsAndDisabledAuditingRecordNothing() {
        for (String path : new String[]{"/actuator/health", "/api/v1/audit/screen-views", "/api/rider/ride/v3/api-docs"}) {
            StepVerifier.create(publisher.filter(exchange(HttpMethod.GET, path, null), e -> Mono.empty())).verifyComplete();
        }
        StepVerifier.create(publisher.filter(exchange(HttpMethod.OPTIONS, "/api/uam/users", null), e -> Mono.empty())).verifyComplete();
        assertThat(queue.size()).isZero();
        props.setEnabled(false);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        MockServerWebExchange ex = exchange(HttpMethod.GET, "/api/uam/users", null);
        StepVerifier.create(publisher.filter(ex, e -> {
            seen.set(e);
            return Mono.empty();
        })).verifyComplete();
        assertThat(queue.size()).isZero();
        assertThat(seen.get()).as("untouched exchange when disabled").isSameAs(ex);
    }

    @Test
    void aBrokenFactoryOrQueueNeverFailsTheRequest() {
        AuditEventFactory exploding = new AuditEventFactory(props, rules) {
            @Override
            public GatewayAuditEvent build(ServerWebExchange exchange, int status, java.time.Instant startedAt, long durationMs,
                                           Captured captured) {
                throw new IllegalStateException("factory bug");
            }
        };
        AuditEventPublisher broken = new AuditEventPublisher(props, rules, exploding, queue, batcher, java.time.Clock.systemUTC());
        StepVerifier.create(broken.filter(exchange(HttpMethod.GET, "/api/uam/users", null), e -> Mono.empty())).verifyComplete();
        assertThat(queue.size()).isZero();
    }

    @Test
    void aFullQueueDropsTheOldestWithoutBlockingTheRequest() {
        AuditEventQueue tiny = new AuditEventQueue(2, Long.MAX_VALUE);
        AuditEventPublisher p = new AuditEventPublisher(props, rules, factory, tiny, batcher, java.time.Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            StepVerifier.create(p.filter(exchange(HttpMethod.GET, "/api/uam/users", null), e -> Mono.empty())).verifyComplete();
        }
        assertThat(tiny.size()).isEqualTo(2);
        assertThat(tiny.dropped()).isEqualTo(3);
    }

    @Test
    void storesTheQueryOnlyWhenEnabledAndWithSensitiveValuesReplaced() {
        MockServerWebExchange off = exchange(HttpMethod.GET, "/api/uam/users?access_token=abc&page=1", null);
        StepVerifier.create(publisher.filter(off, e -> Mono.empty())).verifyComplete();
        assertThat(queue.drain(1).get(0).getDetails()).isNull();
        props.setIncludeQuery(true);
        MockServerWebExchange on = exchange(HttpMethod.GET, "/api/uam/users?access_token=abc&page=1", null);
        StepVerifier.create(publisher.filter(on, e -> Mono.empty())).verifyComplete();
        assertThat(queue.drain(1).get(0).getDetails()).containsEntry("query", "access_token=***&page=1");
    }

    @Test
    void bodiesFlowUnchangedAndAreCapturedWithTheirHeaders() {
        String requestBody = "{\"name\":\"Ann\",\"password\":\"hunter2\"}";
        MockServerWebExchange ex = exchange(HttpMethod.POST, "/api/rider/config/config/settings", requestBody);
        StringBuilder forwarded = new StringBuilder();
        StepVerifier.create(publisher.filter(ex, e -> {
            e.getResponse().setStatusCode(HttpStatus.CREATED);
            e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            e.getResponse().getHeaders().set("Set-Cookie", "sid=1");
            return e.getRequest().getBody().doOnNext(b -> {
                byte[] bytes = new byte[b.readableByteCount()];
                b.read(bytes);
                forwarded.append(new String(bytes, StandardCharsets.UTF_8));
            }).then(e.getResponse().writeWith(Flux.just(buffer("{\"accessToken\":\"jwt\",\"id\":5}"))));
        })).verifyComplete();

        assertThat(forwarded.toString()).as("the backend still receives the exact request body").isEqualTo(requestBody);
        StepVerifier.create(ex.getResponse().getBodyAsString()).expectNext("{\"accessToken\":\"jwt\",\"id\":5}").verifyComplete();

        // the queued event holds the raw capture; redaction happens when the batcher sends it
        GatewayAuditEvent raw = queue.drain(1).get(0);
        assertThat(raw.getRequestBody()).isEqualTo(requestBody);
        GatewayAuditEvent redacted = new AuditEventRedactor(props, rules).redact(raw);
        assertThat(redacted.getRequestBody()).contains("\"name\":\"Ann\"").contains("\"password\":\"***\"").doesNotContain("hunter2");
        assertThat(redacted.getResponseBody()).contains("\"accessToken\":\"***\"").contains("\"id\":5");
        assertThat(redacted.getRequestHeaders()).containsEntry("Authorization", "***").containsEntry("User-Agent", "Mozilla/5.0");
        assertThat(redacted.getResponseHeaders()).containsEntry("Set-Cookie", "***");
        assertThat(redacted.getRequestBodyTruncated()).isFalse();
        assertThat(redacted.getRequestBodySize()).isEqualTo((long) requestBody.length());
    }

    @Test
    void sensitivePathsNeverCaptureBodiesButKeepRedactedHeaders() {
        MockServerWebExchange ex = exchange(HttpMethod.POST, "/api/uam/identity/auth/login", "{\"phone\":\"9876543210\",\"otp\":\"1\"}");
        StepVerifier.create(publisher.filter(ex, e -> {
            e.getResponse().setStatusCode(HttpStatus.OK);
            e.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return e.getRequest().getBody().then(e.getResponse().writeWith(Flux.just(buffer("{\"accessToken\":\"jwt\"}"))));
        })).verifyComplete();
        GatewayAuditEvent e = new AuditEventRedactor(props, rules).redact(queue.drain(1).get(0));
        assertThat(e.getRequestBody()).isNull();
        assertThat(e.getResponseBody()).isNull();
        assertThat(e.getRequestHeaders()).containsEntry("Authorization", "***");
    }

    @Test
    void captureTogglesAndExcludesAreHonoured() {
        props.setCaptureBodies(false);
        StepVerifier.create(publisher.filter(exchange(HttpMethod.POST, "/api/x/y", "{\"a\":1}"), e -> e.getRequest().getBody().then()))
                .verifyComplete();
        GatewayAuditEvent noBodies = queue.drain(1).get(0);
        assertThat(noBodies.getRequestBody()).isNull();
        assertThat(noBodies.getRequestHeaders()).isNotNull();

        props.setCaptureHeaders(false);
        StepVerifier.create(publisher.filter(exchange(HttpMethod.POST, "/api/x/y", "{\"a\":1}"), e -> Mono.empty())).verifyComplete();
        GatewayAuditEvent nothing = queue.drain(1).get(0);
        assertThat(nothing.getRequestHeaders()).isNull();
        assertThat(nothing.getRequestBody()).isNull();

        props.setCaptureHeaders(true);
        props.setCaptureBodies(true);
        props.setCaptureExcludePaths(new java.util.ArrayList<>(List.of("/api/x/**")));
        StepVerifier.create(publisher.filter(exchange(HttpMethod.POST, "/api/x/y", "{\"a\":1}"), e -> e.getRequest().getBody().then()))
                .verifyComplete();
        GatewayAuditEvent excluded = queue.drain(1).get(0);
        assertThat(excluded.getRequestHeaders()).isNull();
        assertThat(excluded.getRequestBody()).isNull();
        assertThat(excluded.getUserId()).as("the access event itself is still recorded").isEqualTo("u-42");
    }

    @Test
    void channelAndUserTypeInference() {
        assertThat(AuditEventFactory.userType(List.of("CUSTOMER"), true)).isEqualTo("CUSTOMER");
        assertThat(AuditEventFactory.userType(List.of("DRIVER"), true)).isEqualTo("DRIVER");
        assertThat(AuditEventFactory.userType(List.of("INTERNAL_SERVICE"), true)).isEqualTo("SERVICE");
        assertThat(AuditEventFactory.userType(List.of(), false)).isEqualTo("ANONYMOUS");
        assertThat(AuditEventFactory.userType(List.of("OPS"), true)).isEqualTo("STAFF");

        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.set("X-App-Channel", "driver_app");
        assertThat(AuditEventFactory.channel(h, "DRIVER", "DRIVER")).isEqualTo("DRIVER_APP");
        h.set("X-App-Channel", "TOASTER");
        h.set("User-Agent", "Dart/3.4 (dart:io)");
        assertThat(AuditEventFactory.channel(h, "CUSTOMER", "CUSTOMER")).isEqualTo("CUSTOMER_APP");
        h.remove("X-App-Channel");
        h.set("User-Agent", "Mozilla/5.0");
        assertThat(AuditEventFactory.channel(h, "CUSTOMER", "CUSTOMER")).isEqualTo("WEB");
        assertThat(AuditEventFactory.channel(h, "STAFF", "ADMIN")).isEqualTo("ADMIN_WEB");
        assertThat(AuditEventFactory.channel(h, "SERVICE", "INTERNAL_SERVICE")).isEqualTo("SERVICE");
    }

    @Test
    void measuresTheCostOfCaptureEventBuildingAndRedaction() {
        String body = "{\"items\":[" + "{\"name\":\"abc\",\"phone\":\"9876543210\",\"qty\":3},".repeat(250)
                + "{\"password\":\"x\"}]}";
        int rounds = 400;
        AuditEventRedactor redactor = new AuditEventRedactor(props, rules);
        long redactNanos = 0;
        long captureNanos = 0;
        long baselineNanos = 0;
        for (int i = 0; i < rounds + 100; i++) {
            long t0 = System.nanoTime();
            AuditPayloadRedactor.redactBody(body, "application/json");
            long t1 = System.nanoTime();
            AuditBodyCapture capture = new AuditBodyCapture(16_384);
            capture.accept(buffer(body));
            capture.snapshot("application/json", null);
            long t2 = System.nanoTime();
            MockServerWebExchange ex = exchange(HttpMethod.POST, "/api/x/y", body);
            StepVerifier.create(ex.getRequest().getBody().then()).verifyComplete();
            long t3 = System.nanoTime();
            if (i >= 100) {
                redactNanos += t1 - t0;
                captureNanos += t2 - t1;
                baselineNanos += t3 - t2;
            }
        }
        double redactMicros = redactNanos / (double) rounds / 1000.0;
        double captureMicros = captureNanos / (double) rounds / 1000.0;
        System.out.printf("AUDIT-OVERHEAD body=%d bytes: JSON redaction %.0f us, tee+snapshot %.0f us per body "
                + "(mock request+StepVerifier harness alone: %.0f us)%n",
                body.length(), redactMicros, captureMicros, baselineNanos / (double) rounds / 1000.0);
        assertThat(redactMicros).as("generous bound; the measured value is printed above").isLessThan(20_000);
        assertThat(redactor).isNotNull();
    }
}
