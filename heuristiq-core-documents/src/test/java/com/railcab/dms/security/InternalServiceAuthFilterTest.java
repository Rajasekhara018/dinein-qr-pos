package com.railcab.dms.security;

import com.railcab.dms.common.DmsCommonConstants;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalServiceAuthFilterTest {

    private static final String SECRET = "0123456789-0123456789-0123456789-abc";
    private static final String PATH = "/api/v1/dms/internal/document-types/apply";

    private final InternalServiceAuthFilter filter =
            new InternalServiceAuthFilter(new InternalAuthProperties("shared-token", SECRET));

    private static String jwt(String secret, String role, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder().subject("rider-workflow-service").claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + ttlMs)).signWith(key).compact();
    }

    private int run(InternalServiceAuthFilter f, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 200;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void noCredentialWrongTokenAndUserTokensAreRefused() throws Exception {
        assertEquals(401, run(filter, request(PATH)));

        MockHttpServletRequest wrong = request(PATH);
        wrong.addHeader(DmsCommonConstants.SERVICE_TOKEN_HEADER, "nope");
        assertEquals(401, run(filter, wrong));

        MockHttpServletRequest user = request(PATH);
        user.addHeader("Authorization", "Bearer " + jwt(SECRET, "ADMIN", 60_000));
        assertEquals(401, run(filter, user));

        MockHttpServletRequest forged = request(PATH);
        forged.addHeader("Authorization", "Bearer " + jwt("another-secret-another-secret-another-secret", "SERVICE", 60_000));
        assertEquals(401, run(filter, forged));

        MockHttpServletRequest expired = request(PATH);
        expired.addHeader("Authorization", "Bearer " + jwt(SECRET, "SERVICE", -60_000));
        assertEquals(401, run(filter, expired));

        // A gateway key or user headers are no credential on this path.
        MockHttpServletRequest gatewayStyle = request(PATH);
        gatewayStyle.addHeader("X-Gateway-Token", "anything");
        gatewayStyle.addHeader("X-User-Id", "u1");
        assertEquals(401, run(filter, gatewayStyle));
    }

    @Test
    void sharedTokenOrServiceJwtIsAccepted() throws Exception {
        MockHttpServletRequest byToken = request(PATH);
        byToken.addHeader(DmsCommonConstants.SERVICE_TOKEN_HEADER, "shared-token");
        assertEquals(200, run(filter, byToken));

        MockHttpServletRequest byJwt = request(PATH);
        byJwt.addHeader("Authorization", "Bearer " + jwt(SECRET, "SERVICE", 60_000));
        assertEquals(200, run(filter, byJwt));
    }

    @Test
    void emptyConfigurationAcceptsNothing() throws Exception {
        InternalServiceAuthFilter closed = new InternalServiceAuthFilter(new InternalAuthProperties("", ""));
        MockHttpServletRequest r = request(PATH);
        r.addHeader(DmsCommonConstants.SERVICE_TOKEN_HEADER, "");
        r.addHeader("Authorization", "Bearer " + jwt(SECRET, "SERVICE", 60_000));
        assertEquals(401, run(closed, r));
    }

    @Test
    void otherPathsAreNotTouchedAndGatewayFilterSkipsInternalPaths() throws Exception {
        assertEquals(200, run(filter, request("/api/v1/dms/document-types")));
        assertTrue(InternalServiceAuthFilter.isInternalPath(PATH));
        assertFalse(InternalServiceAuthFilter.isInternalPath("/api/v1/dms/document-types"));
        assertFalse(InternalServiceAuthFilter.isInternalPath(null));

        GatewayAuthFilter gateway = new GatewayAuthFilter("key");
        MockHttpServletRequest internal = request(PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        gateway.doFilter(internal, response, chain);
        assertNotNull(chain.getRequest(), "the gateway-token filter lets internal paths through to their own filter");

        MockHttpServletRequest ordinary = request("/api/v1/dms/document-types");
        MockHttpServletResponse denied = new MockHttpServletResponse();
        gateway.doFilter(ordinary, denied, new MockFilterChain());
        assertEquals(401, denied.getStatus());
        assertNull(denied.getForwardedUrl());
    }
}
