package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class VerifiedApplicationTest {
    @Test void applicationsAreCarriedFromTheSignedClaimsWithoutCoercingOtherTypes() {
        String secret="verified-application-test-secret-minimum-256-bits-padding";
        var properties=new GatewayProperties(); properties.getAuth().setJwtSecret(secret);
        var provider=new HmacJwtAuthProvider(properties);
        String token=Jwts.builder().subject("user-a").claim("roles",List.of("CUSTOMER"))
                .claim("apps",List.of("PASUPU_KUMKUMA_MATRIMONY_APP",42,""))
                .expiration(new java.util.Date(System.currentTimeMillis()+60000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
        assertThat(provider.toAuthentication(token).getDetails()).isEqualTo(
                new CallerDetails(null,false,List.of("PASUPU_KUMKUMA_MATRIMONY_APP")));
    }
}
