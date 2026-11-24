package com.heuristiq.gateway.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.auth.provider.AuthProviderRouter;
import com.heuristiq.gateway.auth.provider.HmacJwtAuthProvider;
import com.heuristiq.gateway.auth.service.UserStatusCacheService;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.config.RidePublicRouteConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Public live-trip tracking, bound from the real application.properties: GET passes without a token (the share token
 * header goes through untouched), every other method and every sub-path still needs a token.
 */
class PublicTrackJwtFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-testing-minimum-256-bits-padding!!";

    private ReactiveJwtAuthenticationFilter filter;
    private PathMatchersConfig real;

    @BeforeEach
    void setUp() throws Exception {
        Properties raw = new Properties();
        try (var in = getClass().getResourceAsStream("/application.properties")) {
            raw.load(in);
        }
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new PropertiesPropertySource("app", raw));
        real = Binder.get(env).bind("pathmatchers", PathMatchersConfig.class).get();
        GatewayProperties props = new GatewayProperties();
        props.getAuth().setJwtSecret(SECRET);
        props.getAuth().setCustomerJwtSecret(SECRET);
        props.getInternal().setSecretKeys("k1");
        props.getInternal().setGatewayForwardKey("fwd");
        filter = new ReactiveJwtAuthenticationFilter(new AuthProviderRouter(props, List.of(new HmacJwtAuthProvider(props))),
                props, real, mock(UserStatusCacheService.class), new ObjectMapper(), Schedulers.immediate());
    }

    private ServerWebExchange run(HttpMethod method, String path, AtomicReference<ServerWebExchange> forwarded) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(RidePublicRouteConstants.SHARE_TOKEN_HEADER, "share-123")
                .header("X-User-Id", "forged").build());
        WebFilterChain chain = e -> {
            forwarded.set(e);
            return Mono.empty();
        };
        filter.filter(ex, chain).block();
        return ex;
    }

    @Test
    void thePropertyIsBoundAndListedAsGetOnly() {
        assertThat(real.getRiderRidePublicTrack()).isEqualTo(RidePublicRouteConstants.PUBLIC_TRACK_PATH);
        assertThat(real.publicGetOnlyPaths()).containsExactly(RidePublicRouteConstants.PUBLIC_TRACK_PATH,
                "/api/v1/onboarding/*/franchise-options", "/api/v1/onboarding/*/franchise-places");
        assertThat(new PathMatchersConfig().publicGetOnlyPaths()).isEmpty();
    }

    @Test
    void theDriverPlaceSearchIsPublicForGetOnly() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        run(HttpMethod.GET, "/api/v1/onboarding/DRIVER/franchise-places", forwarded);
        assertThat(forwarded.get()).isNotNull();

        AtomicReference<ServerWebExchange> posted = new AtomicReference<>();
        ServerWebExchange post = run(HttpMethod.POST, "/api/v1/onboarding/DRIVER/franchise-places", posted);
        assertThat(posted.get()).isNull();
        assertThat(post.getResponse().getStatusCode().value()).isEqualTo(401);

        AtomicReference<ServerWebExchange> deeper = new AtomicReference<>();
        ServerWebExchange sub = run(HttpMethod.GET, "/api/v1/onboarding/DRIVER/franchise-places/x", deeper);
        assertThat(deeper.get()).as("only the exact path is public").isNull();
        assertThat(sub.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void foodImagesLoadWithoutATokenButUploadingOneNeedsASignedInUser() {
        // An <img> cannot send the bearer token: restaurant, category and menu pictures are public, as the customer app
        // shows them before sign-in. Uploading stays behind sign-in (and the FOOD_IMAGE_UPLOAD_CREATE privilege).
        AtomicReference<ServerWebExchange> image = new AtomicReference<>();
        run(HttpMethod.GET, "/api/v1/food/media/images/05853fea-2ea5-4943-bc76-9461a120ac2a", image);
        assertThat(image.get()).isNotNull();

        AtomicReference<ServerWebExchange> upload = new AtomicReference<>();
        ServerWebExchange post = run(HttpMethod.POST, "/api/v1/food/admin/uploads/images", upload);
        assertThat(upload.get()).isNull();
        assertThat(post.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void theDriverFranchiseOptionsArePublicForGetOnly() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        run(HttpMethod.GET, "/api/v1/onboarding/DRIVER/franchise-options", forwarded);
        assertThat(forwarded.get()).isNotNull();

        AtomicReference<ServerWebExchange> posted = new AtomicReference<>();
        ServerWebExchange post = run(HttpMethod.POST, "/api/v1/onboarding/DRIVER/franchise-options", posted);
        assertThat(posted.get()).isNull();
        assertThat(post.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void getPassesWithoutATokenKeepingTheShareTokenAndDroppingForgedIdentity() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        run(HttpMethod.GET, RidePublicRouteConstants.PUBLIC_TRACK_PATH, forwarded);

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(RidePublicRouteConstants.SHARE_TOKEN_HEADER)).isEqualTo("share-123");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void otherMethodsAndSubPathsNeedAToken() {
        for (HttpMethod m : List.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
            ServerWebExchange ex = run(m, RidePublicRouteConstants.PUBLIC_TRACK_PATH, forwarded);
            assertThat(forwarded.get()).as(m.name()).isNull();
            assertThat(ex.getResponse().getStatusCode()).as(m.name()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        ServerWebExchange sub = run(HttpMethod.GET, RidePublicRouteConstants.PUBLIC_TRACK_PATH + "/extra", forwarded);
        assertThat(forwarded.get()).isNull();
        assertThat(sub.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
