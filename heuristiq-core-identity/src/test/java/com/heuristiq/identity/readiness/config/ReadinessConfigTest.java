package com.heuristiq.identity.readiness.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.identity.application.SessionOrganizationResolver;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.readiness.adapter.out.ReadinessAuthMode;
import com.heuristiq.identity.readiness.application.ApplicationReadinessUseCase;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ServiceReadinessPort;
import com.heuristiq.identity.readiness.domain.ServiceReport;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort;
import com.sun.net.httpserver.HttpServer;

/** The wiring: configured endpoints override the built-in defaults, and the executor is bounded and closable. */
class ReadinessConfigTest {

    private HttpServer server;
    private final List<String> hits = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            hits.add(exchange.getRequestURI().getPath() + " token=" + exchange.getRequestHeaders().getFirst("X-Rider-Service-Token"));
            byte[] body = "{\"responseObject\":{\"checks\":[]}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static ReadinessProperties properties(Map<String, ReadinessProperties.Service> services) {
        return new ReadinessProperties(Duration.ofSeconds(4), Duration.ofSeconds(30), 100, 4, 8, Duration.ofSeconds(2), 2,
                10, 5, 50f, Duration.ofSeconds(30), Duration.ofMinutes(5), services);
    }

    private static UamProperties uamProperties() {
        UamProperties uam = new UamProperties();
        uam.getAuth().setJwtSecret("a-very-long-test-secret-for-hmac-sha-256-signing!!");
        return uam;
    }

    @Test
    void aConfiguredServiceUsesItsBaseUrlPathAndTheServiceToken() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ServiceReadinessPort port = new ReadinessConfig().serviceReadinessPort(properties(Map.of(
                "wallet", new ReadinessProperties.Service(base, "/custom/readiness", ReadinessAuthMode.SERVICE_TOKEN),
                "nms", new ReadinessProperties.Service(" " + base + " ", " ", null))),
                WebClient.builder(), uamProperties(), "tok");

        ServiceReport wallet = port.fetch(ReadinessSource.WALLET, "BEST_PURCHASE_APP", "org-a");
        ServiceReport nms = port.fetch(ReadinessSource.NMS, "BEST_PURCHASE_APP", "org-a");

        assertThat(wallet.answered()).isTrue();
        assertThat(nms.answered()).isTrue();
        assertThat(hits).containsExactly("/custom/readiness token=tok", "/api/v1/nms/internal/readiness token=tok");
    }

    @Test
    void everyServiceHasABuiltInDefault() {
        assertThat(ReadinessConfigConstants.DEFAULTS).containsOnlyKeys(ReadinessSource.WALLET, ReadinessSource.REPORTS,
                ReadinessSource.ONBOARDING, ReadinessSource.DMS, ReadinessSource.NMS);
        assertThat(ReadinessConfigConstants.DEFAULTS.get(ReadinessSource.ONBOARDING).auth()).isEqualTo(ReadinessAuthMode.SERVICE_JWT);
        assertThat(ReadinessConfigConstants.DEFAULTS.get(ReadinessSource.ONBOARDING).path()).isEqualTo("/api/v1/internal/readiness");
        assertThat(ReadinessConfigConstants.DEFAULTS.get(ReadinessSource.DMS).path()).isEqualTo("/api/v1/dms/internal/readiness");
        assertThat(ReadinessConfigConstants.DEFAULTS.get(ReadinessSource.REPORTS).path()).isEqualTo("/api/v1/reports/internal/readiness");
        assertThat(properties(null).services()).isEmpty();
    }

    @Test
    void theUseCaseAndFactsAreWired() {
        ReadinessConfig config = new ReadinessConfig();
        UamReadinessFactsPort facts = config.uamReadinessFactsPort(mock(SessionOrganizationResolver.class),
                mock(TenantApplicationServiceUseCase.class), mock(ApplicationRepository.class));
        try (ReadinessExecutor executor = config.readinessExecutor(properties(Map.of()))) {
            ApplicationReadinessUseCase useCase = config.applicationReadinessUseCase(properties(Map.of()), facts,
                    mock(ServiceReadinessPort.class), executor,
                    mock(com.heuristiq.identity.readiness.application.port.ReadinessSeverityOverrideRepository.class));
            assertThat(useCase).isNotNull();
            assertThat(facts).isNotNull();
        }
    }

    @Test
    void theSeverityStoresAreWiredForBothPersistenceModes() {
        ReadinessConfig config = new ReadinessConfig();
        org.springframework.data.mongodb.core.MongoTemplate mongo = mock(org.springframework.data.mongodb.core.MongoTemplate.class);

        assertThat(config.jdbcReadinessSeverityOverrides(mock(org.springframework.jdbc.core.JdbcTemplate.class),
                mock(org.springframework.transaction.PlatformTransactionManager.class))).isNotNull();
        assertThat(config.mongoReadinessSeverityOverrides(mongo)).isNotNull();
    }

    @Test
    void theExecutorRefusesBeyondItsBoundsAndStopsOnClose() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        ReadinessExecutor executor = new ReadinessExecutor(1, 1);
        Runnable blocked = () -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        executor.executor().submit(blocked);
        executor.executor().submit(blocked);

        assertThatThrownBy(() -> executor.executor().submit(blocked)).isInstanceOf(RejectedExecutionException.class);
        executor.close();
        assertThat(executor.executor().isShutdown()).isTrue();
        release.countDown();
    }

    @Test
    void theJwtSecretComesFromUamProperties() {
        UamProperties uam = mock(UamProperties.class);
        UamProperties.Auth auth = new UamProperties.Auth();
        auth.setJwtSecret("a-very-long-test-secret-for-hmac-sha-256-signing!!");
        when(uam.getAuth()).thenReturn(auth);

        assertThat(new ReadinessConfig().serviceReadinessPort(properties(Map.of()), WebClient.builder(), uam, "")).isNotNull();
    }
}
