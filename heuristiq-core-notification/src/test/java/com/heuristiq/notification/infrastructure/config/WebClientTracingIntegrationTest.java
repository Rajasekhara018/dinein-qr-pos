package com.heuristiq.notification.infrastructure.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpHeaders;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies trace-context propagation (not just correlation-ID) actually reaches the wire for
 * outbound calls -- the concrete gap fixed in {@link NmsBeans#webClient}, which previously built
 * its own {@code WebClient.builder()} instead of the Boot-managed, Observation-instrumented one,
 * silently skipping tracing for every UAM/email/SMS/WhatsApp vendor call sharing this client.
 * A local loopback HTTP server stands in for the downstream call so this holds without a live
 * collector or network access -- what's being checked is that the W3C traceparent header goes out
 * on the wire, not where any exported span ends up.
 */
// MOCK (the default), not NONE: NMS has both spring-boot-starter-web (servlet) and
// spring-boot-starter-webflux (reactive) on the classpath for Meta WhatsApp's WebClient usage --
// under a forced NONE web-application-type, Spring Security's auto-configuration can't decide
// which flavor's HttpSecurity infrastructure to provide. MOCK sets up a real (mock) servlet
// context, which is all Spring Security needs; this test still only exercises the plain
// `webClient` bean over a real loopback socket, not the mock servlet dispatcher.
//
// spring.config.import=vault:// applies unconditionally, and ClientAuthenticationFactory
// hard-requires a non-empty token to even construct (before "optional:" can gracefully handle
// the subsequent connection failure against a Vault that isn't running in tests). The dummy
// token gets a real 403 back if a real Vault happens to be reachable (e.g. a developer's local
// dev instance from docs/design/Platform/SecretsManagement.md), so jwt.secret -- which has no
// hardcoded fallback since it's a required env var in real deployments -- also needs its own
// test-only value here rather than relying on the Vault-contributed property resolving.
@SpringBootTest(properties = {
        "spring.cloud.vault.token=test-only-dummy-token-vault-is-not-used-in-tests",
        "jwt.secret=test-only-jwt-signing-secret-do-not-use-outside-automated-tests-min-256-bits",
        // Starts an embedded MongoDB for this test, so it also passes on a machine that has no database running.
        "de.flapdoodle.mongodb.embedded.version=7.0.5"
})
class WebClientTracingIntegrationTest {

    private static final Pattern TRACEPARENT = Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    private static HttpServer server;
    private static volatile CompletableFuture<HttpHeaders> capturedHeaders;

    @Autowired
    private WebClient webClient;

    @BeforeAll
    static void startLoopbackServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ping", exchange -> {
            capturedHeaders.complete(HttpHeaders.of(exchange.getRequestHeaders(), (a, b) -> true));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopLoopbackServer() {
        server.stop(0);
    }

    @Test
    void outboundCallCarriesAW3cTraceparentHeader() {
        capturedHeaders = new CompletableFuture<>();
        int port = server.getAddress().getPort();

        webClient.get()
                .uri("http://localhost:" + port + "/ping")
                .retrieve()
                .toBodilessEntity()
                .block();

        HttpHeaders headers = capturedHeaders.join();
        var traceparent = headers.firstValue("traceparent");
        assertThat(traceparent).isPresent();
        assertThat(traceparent.get()).matches(TRACEPARENT);
    }
}
