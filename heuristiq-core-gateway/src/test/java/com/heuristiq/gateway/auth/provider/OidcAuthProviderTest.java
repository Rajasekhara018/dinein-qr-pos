package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/** Generic OIDC provider against a stubbed introspection endpoint: no network. */
class OidcAuthProviderTest {

    private final GatewayProperties props = new GatewayProperties();
    private int calls;
    private String lastUri;
    private String responseJson;
    private HttpStatus responseStatus;

    @BeforeEach
    void setUp() {
        props.getOidc().setIntrospectionUri("http://idp/introspect");
        props.getOidc().setClientId("cid");
        props.getOidc().setClientSecret("sec");
        props.getOidc().getRoleMap().put("ops_lead", "OPS");
        responseJson = "{\"active\":true,\"sub\":\"u-7\",\"roles\":[\"ops-lead\",\"Billing\",\"ops_lead\"]}";
        responseStatus = HttpStatus.OK;
    }

    private OidcAuthProvider provider() {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(req -> {
            calls++;
            lastUri = req.url().toString();
            return Mono.just(ClientResponse.create(responseStatus)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(responseJson).build());
        });
        return new OidcAuthProvider(props, builder);
    }

    private static String jwtWithPayload(String json) {
        return "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    @Test
    void typeAndExternalManagement() {
        assertThat(provider().providerType()).isEqualTo(AuthProviderType.OIDC);
        assertThat(provider().isExternallyManagedToken("t")).isTrue();
    }

    @Test
    void activeTokenIsValidAndCached() {
        OidcAuthProvider p = provider();

        assertThat(p.isTokenValid("t")).isTrue();
        assertThat(p.extractUserId("t")).isEqualTo("u-7");

        assertThat(calls).isEqualTo(1);
        assertThat(lastUri).isEqualTo("http://idp/introspect");
    }

    @Test
    void inactiveTokenIsInvalid() {
        responseJson = "{\"active\":false}";

        assertThat(provider().isTokenValid("t")).isFalse();
    }

    @Test
    void missingIntrospectionUriRejectsEverythingWithoutACall() {
        props.getOidc().setIntrospectionUri(" ");
        OidcAuthProvider p = provider();

        assertThat(p.isTokenValid("t")).isFalse();
        assertThat(p.extractUserId("t")).isNull();
        assertThat(p.extractRoles("t")).isEmpty();
        assertThat(p.extractRole("t")).isNull();
        var auth = p.toAuthentication("t");
        assertThat(auth.getPrincipal()).isNull();
        assertThat(auth.getAuthorities()).isEmpty();
        assertThat(calls).isZero();
    }

    @Test
    void providerErrorMakesTheTokenInvalid() {
        responseStatus = HttpStatus.BAD_GATEWAY;
        OidcAuthProvider p = provider();

        assertThat(p.isTokenValid("t")).isFalse();
        assertThat(p.extractUserId("t")).isNull();
        assertThat(p.extractRole("t")).isNull();
        assertThat(p.extractRoles("t")).isEmpty();
    }

    @Test
    void rolesAreMappedThroughTheConfiguredMapAndUnmappedOnesUppercased() {
        OidcAuthProvider p = provider();

        assertThat(p.extractRoles("t")).containsExactly("OPS", "BILLING");
        assertThat(p.extractRole("t")).isEqualTo("OPS");
    }

    @Test
    void rolesGivenAsDelimitedStringAreSplit() {
        responseJson = "{\"active\":true,\"sub\":\"u\",\"roles\":\"a, b  c\"}";

        assertThat(provider().extractRoles("t")).containsExactly("A", "B", "C");
    }

    @Test
    void absentOrUnsupportedRolesClaimMeansNoRoles() {
        responseJson = "{\"active\":true,\"sub\":\"u\",\"roles\":42}";
        assertThat(provider().extractRoles("t1")).isEmpty();

        responseJson = "{\"active\":true,\"sub\":\"u\",\"roles\":\"  \"}";
        assertThat(provider().extractRoles("t2")).isEmpty();
    }

    @Test
    void customClaimNamesAreHonoured() {
        props.getOidc().setSubjectClaim("uid");
        props.getOidc().setRolesClaim("groups");
        responseJson = "{\"active\":true,\"uid\":\"x1\",\"groups\":[\"admins\"]}";

        var auth = provider().toAuthentication("t");

        assertThat(auth.getPrincipal()).isEqualTo("x1");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMINS");
    }

    @Test
    void missingSubjectGivesNullIdentity() {
        responseJson = "{\"active\":true}";
        OidcAuthProvider p = provider();

        assertThat(p.extractUserId("t")).isNull();
        assertThat(p.toAuthentication("t").getPrincipal()).isNull();
    }

    @Test
    void expiryIsReadLocally() {
        OidcAuthProvider p = provider();
        long now = Instant.now().getEpochSecond();

        assertThat(p.isTokenExpired(jwtWithPayload("{\"exp\":" + (now - 5) + "}"))).isTrue();
        assertThat(p.isTokenExpired(jwtWithPayload("{\"exp\":" + (now + 500) + "}"))).isFalse();
        assertThat(p.isTokenExpired(jwtWithPayload("{}"))).isFalse();
        assertThat(p.isTokenExpired("opaque")).isTrue();
        assertThat(p.isTokenExpired("a.%%.c")).isTrue();
        assertThat(calls).isZero();
    }
}
