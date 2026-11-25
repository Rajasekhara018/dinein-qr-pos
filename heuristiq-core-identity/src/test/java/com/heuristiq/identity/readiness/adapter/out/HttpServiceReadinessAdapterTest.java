package com.heuristiq.identity.readiness.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.identity.readiness.domain.ReadinessCheck;
import com.heuristiq.identity.readiness.domain.ReadinessConstants;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.ServiceReport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The outbound adapter against a local HTTP server: path and query, credentials, correlation id, one retry on a transient
 * failure, and every way a service can fail to give a usable answer.
 */
class HttpServiceReadinessAdapterTest {

    private static final String SECRET = "a-very-long-test-secret-for-hmac-sha-256-signing!!";
    private static final String WALLET_PATH = "/api/v1/wallet/internal/readiness";
    private static final String ONBOARDING_PATH = "/api/v1/internal/readiness";
    private static final String REPORT = "{\"success\":true,\"responseObject\":{\"service\":\"wallet\",\"checks\":["
            + "{\"id\":\"WALLET_APP_REGISTERED\",\"status\":\"PASS\",\"messageKey\":\"readiness.wallet.appRegistered.pass\","
            + "\"params\":{\"displayName\":\"Shop\",\"count\":3}},"
            + "{\"id\":\"WALLET_APP_ENABLED\",\"status\":\"FAIL\",\"messageKey\":\"readiness.wallet.appEnabled.fail\","
            + "\"fixPath\":\"/wallet/admin/apps?application=BEST_PURCHASE_APP\"}]}}";

    private HttpServer server;
    private final List<HttpExchangeRecord> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Handler handler = exchange -> respond(exchange, 200, REPORT);
    private final CountDownLatch never = new CountDownLatch(1);

    @FunctionalInterface
    interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }

    record HttpExchangeRecord(String path, String query, String serviceToken, String authorization, String correlationId) {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", exchange -> {
            requests.add(new HttpExchangeRecord(exchange.getRequestURI().getPath(), exchange.getRequestURI().getQuery(),
                    exchange.getRequestHeaders().getFirst("X-Rider-Service-Token"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Correlation-ID")));
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        never.countDown();
        server.stop(0);
        MDC.clear();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private HttpServiceReadinessAdapter adapter(String serviceToken, Duration timeout) {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ResilienceProfile profile = ResilienceProfile.defaults().withCallTimeout(timeout).withMaxAttempts(2);
        return new HttpServiceReadinessAdapter(Map.of(
                ReadinessSource.WALLET, new HttpServiceReadinessAdapter.Endpoint(WALLET_PATH, ReadinessAuthMode.SERVICE_TOKEN,
                        WebClient.builder().baseUrl(base).build(), new ResilientCaller("readiness-wallet-test", profile)),
                ReadinessSource.ONBOARDING, new HttpServiceReadinessAdapter.Endpoint(ONBOARDING_PATH, ReadinessAuthMode.SERVICE_JWT,
                        WebClient.builder().baseUrl(base).build(), new ResilientCaller("readiness-onboarding-test", profile))),
                serviceToken, new ServiceJwtMinter(SECRET, Duration.ofMinutes(5), Clock.fixed(Instant.now(), ZoneOffset.UTC)));
    }

    private HttpServiceReadinessAdapter adapter() {
        return adapter("svc-token", Duration.ofSeconds(2));
    }

    @Test
    void readsTheReportAndSendsTheServiceTokenAndCorrelationId() {
        MDC.put("correlationId", "corr-42");

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.answered()).isTrue();
        assertThat(report.checks()).extracting(ReadinessCheck::id).containsExactly("WALLET_APP_REGISTERED", "WALLET_APP_ENABLED");
        ReadinessCheck registered = report.checks().get(0);
        assertThat(registered.status()).isEqualTo(ReadinessStatus.PASS);
        assertThat(registered.params()).containsEntry("displayName", "Shop").containsEntry("count", "3");
        assertThat(report.checks().get(1).fixPath()).isEqualTo("/wallet/admin/apps?application=BEST_PURCHASE_APP");
        HttpExchangeRecord request = requests.get(0);
        assertThat(request.path()).isEqualTo(WALLET_PATH);
        assertThat(request.query()).isEqualTo("applicationCode=BEST_PURCHASE_APP&tenantId=org-a");
        assertThat(request.serviceToken()).isEqualTo("svc-token");
        assertThat(request.authorization()).isNull();
        assertThat(request.correlationId()).isEqualTo("corr-42");
    }

    @Test
    void onboardingGetsAServiceBearerTokenInsteadOfTheServiceToken() {
        handler = exchange -> respond(exchange, 200, "{\"status\":\"SUCCESS\",\"data\":{\"checks\":[{\"id\":\"ONBOARDING_TYPE_ACTIVE\",\"status\":\"PASS\"}]}}");

        ServiceReport report = adapter().fetch(ReadinessSource.ONBOARDING, "BESTRIDE_RIDER_APP", "org-a");

        assertThat(report.answered()).isTrue();
        assertThat(report.checks()).extracting(ReadinessCheck::id).containsExactly("ONBOARDING_TYPE_ACTIVE");
        HttpExchangeRecord request = requests.get(0);
        assertThat(request.path()).isEqualTo(ONBOARDING_PATH);
        assertThat(request.serviceToken()).isNull();
        assertThat(request.correlationId()).isNull();
        assertThat(request.authorization()).startsWith("Bearer ");
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(request.authorization().substring("Bearer ".length())).getPayload();
        assertThat(claims.get("role", String.class)).isEqualTo("SERVICE");
        assertThat(claims.getSubject()).isEqualTo("rider-uam");
    }

    @Test
    void noServiceTokenIsSentWhenNoneIsConfigured() {
        adapter("", Duration.ofSeconds(2)).fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(requests.get(0).serviceToken()).isNull();
    }

    @Test
    void aTransientFailureIsRetriedOnce() {
        AtomicInteger attempts = new AtomicInteger();
        handler = exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "");
            } else {
                respond(exchange, 200, REPORT);
            }
        };

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.answered()).isTrue();
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void aServiceThatKeepsFailingIsUnavailableAfterAtMostTwoAttempts() {
        handler = exchange -> respond(exchange, 503, "");

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.UNAVAILABLE);
        assertThat(report.reason()).isEqualTo("FAILED");
        assertThat(requests).hasSize(2);
    }

    @Test
    void aRefusalIsReportedWithItsStatusAndNotRetried() {
        handler = exchange -> respond(exchange, 401, "{\"success\":false}");

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.REFUSED);
        assertThat(report.reason()).isEqualTo("HTTP_401");
        assertThat(requests).hasSize(1);
    }

    @Test
    void garbageIsMalformed() {
        handler = exchange -> respond(exchange, 200, "<html>login</html>");

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.MALFORMED);
        assertThat(report.reason()).isEqualTo(ReadinessConstants.REASON_MALFORMED_RESPONSE);
    }

    @Test
    void anEmptyBodyIsMalformed() {
        handler = exchange -> respond(exchange, 200, "");

        assertThat(adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a").outcome())
                .isEqualTo(ServiceReport.Outcome.MALFORMED);
    }

    @Test
    void aSlowServiceTimesOut() {
        handler = exchange -> {
            try {
                never.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, REPORT);
        };

        ServiceReport report = adapter("svc-token", Duration.ofMillis(200)).fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.UNAVAILABLE);
        assertThat(report.reason()).isEqualTo("TIMEOUT");
    }

    @Test
    void aServiceThatIsNotRunningIsUnavailable() {
        server.stop(0);

        ServiceReport report = adapter().fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.UNAVAILABLE);
    }

    @Test
    void aServiceWithoutAnEndpointIsUnavailable() {
        ServiceReport report = adapter().fetch(ReadinessSource.NMS, "BEST_PURCHASE_APP", "org-a");

        assertThat(report.outcome()).isEqualTo(ServiceReport.Outcome.UNAVAILABLE);
        assertThat(report.reason()).isEqualTo(ReadinessConstants.REASON_ERROR);
        assertThat(requests).isEmpty();
    }
}
