package com.heuristiq.gateway.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.auth.provider.AuthProviderRouter;
import com.heuristiq.gateway.auth.service.UserStatusCacheService;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Runs the real security filter chains inside a minimal WebFlux context: unauthenticated callers are rejected on
 * protected routes, public paths and the Swagger/CORS rules behave as configured.
 */
class GatewaySecurityConfigTest {

    @RestController
    static class Probe {
        @GetMapping("/actuator/health")
        String health() {
            return "up";
        }

        @GetMapping("/api/v1/private/thing")
        String secret() {
            return "secret";
        }

        @GetMapping("/v3/api-docs")
        String docs() {
            return "docs";
        }

        @GetMapping("/api/v1/uam/identity/auth/login")
        String login() {
            return "login";
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/uam/identity/auth/biometric/challenge")
        String biometricChallenge() {
            return "challenge";
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/uam/identity/auth/biometric/verify")
        String biometricVerify() {
            return "verified";
        }

        @GetMapping("/api/v1/uam/identity/me/biometric-devices")
        String myBiometricDevices() {
            return "devices";
        }

        @GetMapping("/api/v1/config/public/support-contacts")
        String supportContacts() {
            return "contacts";
        }

        @GetMapping("/api/v1/ride/public/track")
        String track() {
            return "track";
        }

        @org.springframework.web.bind.annotation.PostMapping("/api/v1/ride/public/track")
        String trackWrite() {
            return "written";
        }
    }

    @Configuration
    @EnableWebFlux
    @EnableWebFluxSecurity
    @Import({GatewaySecurityConfig.class, Probe.class})
    static class TestContext {
        @Bean
        AuthProviderRouter authProviderRouter() {
            return mock(AuthProviderRouter.class);
        }

        @Bean
        UserStatusCacheService userStatusCacheService() {
            return mock(UserStatusCacheService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        GatewayProperties gatewayProperties() {
            GatewayProperties p = new GatewayProperties();
            p.getCors().setAllowedOrigins("https://app.example.com, http://localhost:4200");
            p.getSwagger().setFrameAncestors("https://docs.example.com");
            return p;
        }

        @Bean
        PathMatchersConfig pathMatchersConfig() {
            PathMatchersConfig c = new PathMatchersConfig();
            c.setUamIdentityAuth("/api/v1/uam/identity/auth/**");
            c.setActuator("/actuator");
            c.setRiderRidePublicTrack("/api/v1/ride/public/track");
            c.setRiderConfigPublic("/api/v1/config/public");
            return c;
        }

        @Bean
        Scheduler blockingOpsScheduler() {
            return Schedulers.immediate();
        }
    }

    private AnnotationConfigApplicationContext context;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestContext.class);
        client = WebTestClient.bindToApplicationContext(context).apply(
                org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity()).configureClient().baseUrl("http://localhost").build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void protectedRouteWithoutCredentialsIsRejected() {
        client.get().uri("/api/v1/private/thing").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void publicPathsAreServedWithoutCredentials() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("up");
        client.get().uri("/api/v1/uam/identity/auth/login").exchange().expectStatus().isOk();
    }

    @Test
    void biometricSignInIsPublicLikeLoginButTheDeviceListNeedsAToken() {
        // Covered by the existing uam-identity-auth matcher (/api/v1/uam/identity/auth/**): no new public path was needed.
        client.post().uri("/api/v1/uam/identity/auth/biometric/challenge").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("challenge");
        client.post().uri("/api/v1/uam/identity/auth/biometric/verify").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("verified");
        client.get().uri("/api/v1/uam/identity/me/biometric-devices").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void everyPathUnderAPublicPrefixIsServedLikeTheOtherGatewayFiltersTreatIt() {
        // the JWT filter skips the token under /api/v1/config/public/**, so this check must permit the same sub-paths
        client.get().uri("/api/v1/config/public/support-contacts").exchange().expectStatus().isOk()
                .expectBody(String.class).isEqualTo("contacts");
    }

    @Test
    void theTrackingPathIsPublicForGetOnly() {
        client.get().uri("/api/v1/ride/public/track").header("X-Share-Token", "t").exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("track");
        client.post().uri("/api/v1/ride/public/track").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/ride/public/track/x").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void swaggerChainAllowsFramingFromTheConfiguredOriginOnly() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Content-Security-Policy", "frame-ancestors 'self' https://docs.example.com")
                .expectHeader().doesNotExist("X-Frame-Options");
    }

    @Test
    void corsPreflightFromAllowedOriginIsAnswered() {
        client.options().uri("/api/v1/private/thing")
                .header("Origin", "https://app.example.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange().expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "https://app.example.com")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void corsPreflightFromUnknownOriginIsRefused() {
        client.options().uri("/api/v1/private/thing")
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange().expectStatus().isForbidden();
    }

    @Test
    void paymentCallbackCorsListIsSeparateFromTheGlobalOne() {
        var source = context.getBean(GatewaySecurityConfig.class).corsConfigurationSource();
        var callback = source.getCorsConfiguration(org.springframework.mock.web.server.MockServerWebExchange
                .from(org.springframework.mock.http.server.reactive.MockServerHttpRequest
                        .get("/api/payment/callback/razorpay").build()));
        var global = source.getCorsConfiguration(org.springframework.mock.web.server.MockServerWebExchange
                .from(org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/v1/thing").build()));

        assertThat(callback.getAllowedOriginPatterns()).contains("null");
        assertThat(global.getAllowedOriginPatterns()).containsExactly("https://app.example.com",
                "http://localhost:4200");
        assertThat(global.getAllowedMethods()).contains("PATCH", "DELETE");
        assertThat(global.checkOrigin("https://app.example.com")).isEqualTo("https://app.example.com");
        assertThat(global.checkOrigin("https://evil.example.com")).isNull();
    }

}
