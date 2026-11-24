package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class AuditPathRulesTest {

    private final AuditProperties props = new AuditProperties();
    private final AuditPathRules rules = new AuditPathRules(props);

    @Test
    void healthDocsStaticAndScreenViewPostsAreNotAudited() {
        for (String path : new String[]{"/actuator/health", "/api/bookings/actuator/health", "/api/rider/config/v3/api-docs",
                "/v3/api-docs/swagger-config", "/swagger-ui/index.html", "/api/uam/swagger-ui.html", "/javadoc/index.html",
                "/api/rider/ride/javadoc/index.html", "/fallback/admin", "/favicon.ico", "/static/app.js", "/x/main.css",
                "/api/v1/audit/screen-views"}) {
            assertThat(rules.skip(path, "GET")).as(path).isTrue();
        }
        assertThat(rules.skip("/api/rider/config/config/settings", "OPTIONS")).isTrue();
        for (String path : new String[]{"/api/rider/config/config/settings", "/api/uam/users/5", "/api/rider/audit/audit/events"}) {
            assertThat(rules.skip(path, "GET")).as(path).isFalse();
        }
    }

    @Test
    void idLikeSegmentsAreNormalised() {
        assertThat(rules.normalise("/api/uam/users/12345/orders/3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
                .isEqualTo("/api/uam/users/{id}/orders/{id}");
        assertThat(rules.normalise("/api/x/507f1f77bcf86cd799439011/")).isEqualTo("/api/x/{id}");
        assertThat(rules.normalise("/api/rider/config/config/settings/city.name")).isEqualTo("/api/rider/config/config/settings/city.name");
        props.setNormalisePath(false);
        assertThat(rules.normalise("/api/users/12345")).isEqualTo("/api/users/12345");
    }

    @Test
    void sensitiveQueryValuesAreReplacedAndNamesKept() {
        String out = rules.sanitizeQuery("page=2&access_token=abc123&apiKey=k&q=hello&OTP=1234&x");
        assertThat(out).isEqualTo("page=2&access_token=***&apiKey=***&q=hello&OTP=***&x");
        assertThat(rules.sanitizeQuery(null)).isNull();
        assertThat(rules.sanitizeQuery("  ")).isNull();
        assertThat(rules.sanitizeQuery("a=" + "b".repeat(900))).hasSize(500);
    }

    @Test
    void sensitivePathsSkipBodiesAndExcludesAreConfigurable() {
        for (String path : new String[]{"/api/uam/identity/auth/login", "/api/v1/uam/identity/auth/refresh",
                "/api/rider/customer/otp/send", "/api/uam/users/5/change-password", "/api/rider/payment/cards",
                "/api/uam/token/refresh"}) {
            assertThat(rules.bodySkipped(path)).as(path).isTrue();
        }
        assertThat(rules.bodySkipped("/api/rider/config/config/settings")).isFalse();
        assertThat(rules.captureExcluded("/api/rider/location/ping")).isFalse();
        props.getCaptureExcludePaths().add("/api/rider/location/**");
        assertThat(rules.captureExcluded("/api/rider/location/ping")).isTrue();
    }

    @Test
    void internalAuditPathsAreRecognisedInEverySpelling() {
        for (String path : new String[]{"/api/rider/audit/audit/internal/events", "/api/rider/audit/v1/audit/internal/events",
                "/api/v1/rider/audit/audit/internal/events", "/api/rider/audit/AUDIT/Internal/events",
                "/api/rider/audit//audit//internal/events", "/api/rider/audit/audit/internal",
                "/api/rider/audit/audit%2Finternal/events", "/api/rider/audit/%61udit/internal/events",
                "/api/rider/audit/audit/%2569nternal/events", "/api/rider/audit/audit/internal/",
                "/api/v1/audit/internal/events", "/api/v1/audit/audit/internal/events"}) {
            assertThat(AuditPathRules.isInternalAuditPath(path)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/rider/audit/audit/events", "/api/rider/audit/audit/screen-views",
                "/api/rider/audit/audit/events/internal-ish", "/api/rider/config/config/internal/resolve",
                "/api/v1/audit/events", "/api/v1/audit/screen-views", null}) {
            assertThat(AuditPathRules.isInternalAuditPath(path)).as(String.valueOf(path)).isFalse();
        }
    }

    @Test
    void theRouteFilterAnswers404WithoutForwardingInternalPaths() {
        AtomicBoolean forwarded = new AtomicBoolean();
        var filter = AuditPathRules.blockInternalAuditFilter();
        MockServerWebExchange blocked = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, "/api/rider/audit/audit/internal/events").build());
        StepVerifier.create(filter.filter(blocked, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded).isFalse();

        MockServerWebExchange open = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/rider/audit/audit/events").build());
        StepVerifier.create(filter.filter(open, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(forwarded).isTrue();
        assertThat(open.getResponse().getStatusCode()).isNull();
    }
}
