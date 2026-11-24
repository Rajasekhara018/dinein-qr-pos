package com.heuristiq.gateway.auth.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Introspection is served by a stubbed exchange function: no network. */
class KeycloakAuthProviderTest {

    private final List<String> requestUrls = new ArrayList<>();
    private int calls;
    private String responseJson;
    private HttpStatus responseStatus;
    private KeycloakAuthProvider provider;

    @BeforeEach
    void setUp() {
        responseJson = "{\"active\":true,\"sub\":\"user-1\",\"realm_access\":{\"roles\":"
                + "[\"default-roles-railcab\",\"railcab_manager\",\"custom-role\",\"manager\"]}}";
        responseStatus = HttpStatus.OK;
        WebClient.Builder builder = WebClient.builder().exchangeFunction(req -> {
            calls++;
            requestUrls.add(req.url().toString());
            return Mono.just(ClientResponse.create(responseStatus)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE).body(responseJson).build());
        });
        provider = new KeycloakAuthProvider(builder);
        ReflectionTestUtils.setField(provider, "serverUrl", "http://kc");
        ReflectionTestUtils.setField(provider, "realm", "r1");
        ReflectionTestUtils.setField(provider, "clientId", "gw");
        ReflectionTestUtils.setField(provider, "clientSecret", "s");
    }

    private static String jwtWithPayload(String json) {
        return "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    @Test
    void typeAndExternalManagement() {
        assertThat(provider.providerType()).isEqualTo(AuthProviderType.KEYCLOAK);
        assertThat(provider.isExternallyManagedToken("t")).isTrue();
    }

    @Test
    void activeTokenIsValidAndIntrospectionIsCached() {
        assertThat(provider.isTokenValid("tok")).isTrue();
        assertThat(provider.isTokenValid("tok")).isTrue();

        assertThat(calls).isEqualTo(1);
        assertThat(requestUrls.get(0)).isEqualTo("http://kc/realms/r1/protocol/openid-connect/token/introspect");
    }

    @Test
    void inactiveTokenIsInvalid() {
        responseJson = "{\"active\":false}";

        assertThat(provider.isTokenValid("tok")).isFalse();
    }

    @Test
    void introspectionFailureMakesTheTokenInvalidAndYieldsNoIdentity() {
        responseStatus = HttpStatus.INTERNAL_SERVER_ERROR;

        assertThat(provider.isTokenValid("bad")).isFalse();
        assertThat(provider.extractUserId("bad")).isNull();
        assertThat(provider.extractRole("bad")).isNull();
        assertThat(provider.extractRoles("bad")).isEmpty();
    }

    @Test
    void userIdIsTheSubjectClaim() {
        assertThat(provider.extractUserId("tok")).isEqualTo("user-1");
    }

    @Test
    void missingSubjectGivesNullUserId() {
        responseJson = "{\"active\":true}";

        assertThat(provider.extractUserId("tok")).isNull();
    }

    @Test
    void rolesAreMappedDefaultRolesDroppedUnmappedUppercasedAndDeduplicated() {
        assertThat(provider.extractRoles("tok")).containsExactly("MANAGER", "CUSTOM_ROLE");
        assertThat(provider.extractRole("tok")).isEqualTo("MANAGER");
    }

    @Test
    void noRealmAccessOrRolesMeansNoRole() {
        responseJson = "{\"active\":true,\"sub\":\"u\"}";
        assertThat(provider.extractRoles("a")).isEmpty();
        assertThat(provider.extractRole("a")).isNull();

        responseJson = "{\"active\":true,\"realm_access\":{}}";
        assertThat(provider.extractRoles("b")).isEmpty();
    }

    @Test
    void authenticationCarriesPrefixedAuthorities() {
        var auth = provider.toAuthentication("tok");

        assertThat(auth.getPrincipal()).isEqualTo("user-1");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_MANAGER", "ROLE_CUSTOM_ROLE");
    }

    @Test
    void authenticationWithoutSubjectHasNullPrincipal() {
        responseJson = "{\"active\":true}";

        var auth = provider.toAuthentication("tok");

        assertThat(auth.getPrincipal()).isNull();
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void expiryIsReadLocallyFromTheExpClaim() {
        long now = Instant.now().getEpochSecond();

        assertThat(provider.isTokenExpired(jwtWithPayload("{\"exp\":" + (now - 60) + "}"))).isTrue();
        assertThat(provider.isTokenExpired(jwtWithPayload("{\"exp\":" + (now + 600) + "}"))).isFalse();
        assertThat(provider.isTokenExpired(jwtWithPayload("{\"sub\":\"x\"}"))).isFalse();
        assertThat(calls).isZero();
    }

    @Test
    void unparseableTokensCountAsExpired() {
        assertThat(provider.isTokenExpired("opaque")).isTrue();
        assertThat(provider.isTokenExpired("a.!!!.c")).isTrue();
    }
}
