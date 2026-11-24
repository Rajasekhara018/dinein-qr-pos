package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.auth.service.UamSessionPolicyClient;
import com.heuristiq.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HmacJwtAuthProviderPolicyTest {
    static final String SECRET = "test-secret-key-for-unit-testing-minimum-256-bits-padding!!";
    final UamSessionPolicyClient policies = mock(UamSessionPolicyClient.class);
    HmacJwtAuthProvider provider() {
        var props = new GatewayProperties();
        props.getAuth().setJwtSecret(SECRET);
        var provider = new HmacJwtAuthProvider(props);
        ReflectionTestUtils.setField(provider, "sessionPolicies", policies);
        return provider;
    }
    String token(String sid) {
        var builder = Jwts.builder().issuer("railcab-uam").subject("user").claim("role", "OPS")
                .expiration(new Date(System.currentTimeMillis() + 60000));
        if (sid != null) builder.claim("sid", sid);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }
    @Test void gatewayRejectsRevokedPolicySessionsAndTokensWithoutSessionIds() {
        var provider = provider();
        String token = token("session");
        when(policies.active(token)).thenReturn(false);
        assertThat(provider.verify(token).valid()).isFalse();
        assertThat(provider.verify(token(null)).valid()).isFalse();
        when(policies.active(token)).thenReturn(true);
        assertThat(provider.verify(token).valid()).isTrue();
    }
    @Test void enrollmentProofCannotOpenApplicationApisEvenWithACustomIssuer() {
        String proof = Jwts.builder().issuer("custom-uam").subject("user").claim("purpose", "MFA_ENROLLMENT")
                .expiration(new Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(provider().verify(proof).valid()).isFalse();
        verifyNoInteractions(policies);
    }
}
