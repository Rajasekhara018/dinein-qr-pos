package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.config.GatewayProperties;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HmacJwtAuthProvider#verify(String)} parses a token once; its answer must be exactly what the gateway got
 * before from calling isTokenValid, isTokenExpired, toAuthentication and isExternallyManagedToken one by one.
 */
class HmacJwtAuthProviderVerifyTest {

    private static final String ADMIN_SECRET = "test-secret-key-for-unit-testing-minimum-256-bits-padding!!";
    private static final String CUSTOMER_SECRET = "customer-test-secret-key-for-unit-testing-min-256-bits!!";
    private static final String OTHER_SECRET = "a-completely-different-secret-that-is-long-enough-1234567";

    private GatewayProperties props;
    private HmacJwtAuthProvider provider;

    @BeforeEach
    void setUp() {
        props = new GatewayProperties();
        props.getAuth().setJwtSecret(ADMIN_SECRET);
        props.getAuth().setCustomerJwtSecret(CUSTOMER_SECRET);
        provider = new HmacJwtAuthProvider(props);
    }

    /** The old per-request sequence, through the interface's default verify over the single-purpose methods. */
    private TokenVerification oneByOne(String token) {
        IAuthProvider sequential = new IAuthProvider() {
            @Override public AuthProviderType providerType() { return AuthProviderType.HMAC; }
            @Override public boolean isTokenValid(String t) { return provider.isTokenValid(t); }
            @Override public boolean isTokenExpired(String t) { return provider.isTokenExpired(t); }
            @Override public String extractUserId(String t) { return provider.extractUserId(t); }
            @Override public String extractRole(String t) { return provider.extractRole(t); }
            @Override public UsernamePasswordAuthenticationToken toAuthentication(String t) { return provider.toAuthentication(t); }
            @Override public boolean isExternallyManagedToken(String t) { return provider.isExternallyManagedToken(t); }
        };
        return sequential.verify(token);
    }

    private static String token(String secret, Date expiry, java.util.function.UnaryOperator<JwtBuilder> claims) {
        JwtBuilder b = Jwts.builder().subject("user-1").issuedAt(new Date()).expiration(expiry);
        return claims.apply(b).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private static Date in(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000);
    }

    @Test
    void verifyAgreesWithTheOneByOneChecksForEveryKindOfToken() {
        List<String> tokens = new ArrayList<>();
        tokens.add(token(ADMIN_SECRET, in(3600), b -> b.claim("role", "MANAGER")));
        tokens.add(token(ADMIN_SECRET, in(3600), b -> b.claim("roles", List.of("MANAGER", "SUPPORT", "MANAGER"))
                .claim("tenantId", "org-7").claim("orgWide", true).issuer("railcab-uam")));
        tokens.add(token(ADMIN_SECRET, in(3600), b -> b.claim("roles", List.of()).claim("role", "AUDITOR").issuer("RAILCAB-UAM")));
        tokens.add(token(ADMIN_SECRET, in(3600), b -> b.claim("role", 42).claim("tenantId", " ")));
        tokens.add(token(ADMIN_SECRET, in(3600), b -> b.issuer("someone-else").claim("orgWide", "false")));
        tokens.add(token(CUSTOMER_SECRET, in(3600), b -> b.claim("role", "CUSTOMER").claim("tenantId", "default")));
        tokens.add(token(ADMIN_SECRET, in(-3600), b -> b.claim("role", "MANAGER")));
        tokens.add(token(CUSTOMER_SECRET, in(-3600), b -> b.claim("role", "DRIVER")));
        tokens.add(token(OTHER_SECRET, in(3600), b -> b.claim("role", "SUPER_ADMIN")));
        tokens.add("not.a.jwt");
        tokens.add("");
        tokens.add(null);

        for (String t : tokens) {
            TokenVerification expected = oneByOne(t);
            TokenVerification actual = provider.verify(t);
            assertThat(actual.valid()).as("valid for %s", t).isEqualTo(expected.valid());
            assertThat(actual.expired()).as("expired for %s", t).isEqualTo(expected.expired());
            assertThat(actual.externallyManaged()).as("externallyManaged for %s", t).isEqualTo(expected.externallyManaged());
            if (expected.valid()) {
                assertThat(actual.authentication().getPrincipal()).isEqualTo(expected.authentication().getPrincipal());
                assertThat(actual.authentication().getAuthorities()).containsExactlyElementsOf(expected.authentication().getAuthorities());
                assertThat(actual.authentication().getDetails()).isEqualTo(expected.authentication().getDetails());
                assertThat(actual.authentication().isAuthenticated()).isTrue();
            }
        }
    }

    @Test
    void anAcceptedUamTokenCarriesTheCallerAndIsExternallyManaged() {
        String t = token(ADMIN_SECRET, in(3600), b -> b.claim("roles", List.of("MANAGER", "SUPPORT"))
                .claim("tenantId", "org-7").issuer("railcab-uam"));

        TokenVerification v = provider.verify(t);

        assertThat(v.valid()).isTrue();
        assertThat(v.externallyManaged()).isTrue();
        assertThat(v.authentication().getPrincipal()).isEqualTo("user-1");
        assertThat(v.authentication().getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_MANAGER", "ROLE_SUPPORT");
        assertThat(v.authentication().getDetails()).isEqualTo(new CallerDetails("org-7", false));
    }

    @Test
    void aCustomerTokenIsAcceptedThroughTheCustomerSecretFallback() {
        TokenVerification v = provider.verify(token(CUSTOMER_SECRET, in(3600), b -> b.claim("role", "CUSTOMER")));

        assertThat(v.valid()).isTrue();
        assertThat(v.externallyManaged()).isFalse();
    }

    @Test
    void everyRejectedTokenIsReportedAsExpiredAsBefore() {
        assertThat(provider.verify(token(ADMIN_SECRET, in(-60), b -> b)).expired()).isTrue();
        assertThat(provider.verify(token(OTHER_SECRET, in(3600), b -> b)).expired()).isTrue();
        assertThat(provider.verify("not.a.jwt").expired()).isTrue();
    }

    @Test
    void aChangedSecretIsPickedUpInsteadOfTheCachedParser() {
        String signedWithOld = token(ADMIN_SECRET, in(3600), b -> b.claim("role", "MANAGER"));
        assertThat(provider.verify(signedWithOld).valid()).isTrue();

        props.getAuth().setJwtSecret(OTHER_SECRET);

        assertThat(provider.verify(signedWithOld).valid()).isFalse();
        assertThat(provider.verify(token(OTHER_SECRET, in(3600), b -> b.claim("role", "MANAGER"))).valid()).isTrue();
    }

    @Test
    void routerUsesTheHmacResultWhenItAcceptsAndOtherwiseAsksTheConfiguredProvider() {
        props.getIdentityProvider().setType("keycloak");
        AtomicInteger externalCalls = new AtomicInteger();
        UsernamePasswordAuthenticationToken externalAuth =
                new UsernamePasswordAuthenticationToken("kc-user", null, List.of());
        IAuthProvider keycloak = new IAuthProvider() {
            @Override public AuthProviderType providerType() { return AuthProviderType.KEYCLOAK; }
            @Override public boolean isTokenValid(String t) { externalCalls.incrementAndGet(); return "kc-token".equals(t); }
            @Override public boolean isTokenExpired(String t) { return false; }
            @Override public String extractUserId(String t) { return "kc-user"; }
            @Override public String extractRole(String t) { return null; }
            @Override public UsernamePasswordAuthenticationToken toAuthentication(String t) { return externalAuth; }
            @Override public boolean isExternallyManagedToken(String t) { return true; }
        };
        AuthProviderRouter router = new AuthProviderRouter(props, List.of(provider, keycloak));

        TokenVerification uam = router.verify(token(ADMIN_SECRET, in(3600), b -> b.claim("role", "MANAGER")));
        assertThat(uam.authentication().getPrincipal()).isEqualTo("user-1");
        assertThat(externalCalls).hasValue(0);

        TokenVerification external = router.verify("kc-token");
        assertThat(external.authentication()).isSameAs(externalAuth);
        assertThat(external.externallyManaged()).isTrue();

        TokenVerification rejected = router.verify("bogus");
        assertThat(rejected.valid()).isFalse();
        assertThat(rejected.expired()).isFalse();
        assertThat(externalCalls).hasValue(2);
    }
}
