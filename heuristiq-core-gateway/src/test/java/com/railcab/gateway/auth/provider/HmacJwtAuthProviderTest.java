package com.heuristiq.gateway.auth.provider;

import com.heuristiq.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HmacJwtAuthProviderTest {

    private static final String ADMIN_SECRET =
            "test-secret-key-for-unit-testing-minimum-256-bits-padding!!";
    private static final String CUSTOMER_SECRET =
            "customer-test-secret-key-for-unit-testing-min-256-bits!!";

    private HmacJwtAuthProvider provider;

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties();
        props.getAuth().setJwtSecret(ADMIN_SECRET);
        props.getAuth().setCustomerJwtSecret(CUSTOMER_SECRET);
        provider = new HmacJwtAuthProvider(props);
    }

    @Test
    void theSessionOrganizationOfTheTokenIsCarriedAsCallerDetails() {
        String withTenant = io.jsonwebtoken.Jwts.builder().subject("u1").claim("roles", java.util.List.of("MANAGER")).claim("tenantId", "org-7")
                .expiration(futureDate(3600)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        String withoutTenant = buildToken("u2", "MANAGER", futureDate(3600));

        assertEquals(new CallerDetails("org-7"), provider.toAuthentication(withTenant).getDetails());
        assertNull(provider.toAuthentication(withoutTenant).getDetails());
    }

    @Test
    void theOrgWideClaimIsCarriedAsCallerDetailsAndAnOrdinaryTokenHasNone() {
        String orgWide = io.jsonwebtoken.Jwts.builder().subject("u1").claim("roles", java.util.List.of("MANAGER")).claim("tenantId", "org-7").claim("orgWide", true)
                .expiration(futureDate(3600)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();
        String forgedFalse = io.jsonwebtoken.Jwts.builder().subject("u2").claim("roles", java.util.List.of("MANAGER")).claim("tenantId", "org-7").claim("orgWide", false)
                .expiration(futureDate(3600)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();

        assertEquals(new CallerDetails("org-7", true), provider.toAuthentication(orgWide).getDetails());
        assertEquals(new CallerDetails("org-7", false), provider.toAuthentication(forgedFalse).getDetails());
    }

    @Test
    void validToken_isValid() {
        String token = buildToken("user-123", "MANAGER", futureDate(3600));
        assertTrue(provider.isTokenValid(token));
    }

    @Test
    void expiredToken_isInvalid() {
        String token = buildToken("user-123", "MANAGER", pastDate(3600));
        assertFalse(provider.isTokenValid(token));
    }

    @Test
    void expiredToken_isExpired() {
        String token = buildToken("user-123", "MANAGER", pastDate(3600));
        assertTrue(provider.isTokenExpired(token));
    }

    @Test
    void validToken_isNotExpired() {
        String token = buildToken("user-123", "MANAGER", futureDate(3600));
        assertFalse(provider.isTokenExpired(token));
    }

    @Test
    void extractUserId_returnsSubject() {
        String token = buildToken("user-abc", "SUPPORT", futureDate(3600));
        assertEquals("user-abc", provider.extractUserId(token));
    }

    @Test
    void extractRole_returnsRoleClaim() {
        String token = buildToken("user-123", "SUPER_ADMIN", futureDate(3600));
        assertEquals("SUPER_ADMIN", provider.extractRole(token));
    }

    @Test
    void toAuthentication_setsCorrectPrincipalAndAuthority() {
        String token = buildToken("user-xyz", "ACCOUNTANT", futureDate(3600));
        UsernamePasswordAuthenticationToken auth = provider.toAuthentication(token);

        assertEquals("user-xyz", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ACCOUNTANT")));
    }

    // ── Multi-role tokens (UAM's "roles" claim) ──────────────────────────────────
    @Test
    void extractRoles_returnsFullRolesClaim_whenPresent() {
        String token = buildTokenWithRoles("user-multi", List.of("SUPPORT", "AUDITOR"), futureDate(3600));
        assertEquals(List.of("SUPPORT", "AUDITOR"), provider.extractRoles(token));
    }

    @Test
    void extractRoles_fallsBackToSingularRoleClaim_whenRolesClaimAbsent() {
        String token = buildToken("user-single", "MANAGER", futureDate(3600));
        assertEquals(List.of("MANAGER"), provider.extractRoles(token));
    }

    @Test
    void toAuthentication_grantsEveryRole_notJustTheFirst() {
        String token = buildTokenWithRoles("user-multi", List.of("SUPPORT", "AUDITOR"), futureDate(3600));
        UsernamePasswordAuthenticationToken auth = provider.toAuthentication(token);

        assertEquals("user-multi", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT")),
                "First role must still be granted");
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_AUDITOR")),
                "Second role must not be silently dropped");
        assertEquals(2, auth.getAuthorities().size());
    }

    // ── Dual-secret: customer tokens signed with customer secret ─────────────────

    @Test
    void customerToken_signedWithCustomerSecret_isValid() {
        String token = buildTokenWith("customer-001", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET);
        assertTrue(provider.isTokenValid(token),
                "Provider must accept customer tokens signed with customer secret");
    }

    @Test
    void driverToken_signedWithCustomerSecret_isValid() {
        String token = buildTokenWith("driver-001", "DRIVER", futureDate(3600), CUSTOMER_SECRET);
        assertTrue(provider.isTokenValid(token));
    }

    @Test
    void customerToken_extractRole_returnsCustomer() {
        String token = buildTokenWith("customer-001", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET);
        assertEquals("CUSTOMER", provider.extractRole(token));
    }

    @Test
    void customerToken_extractUserId_returnsSubject() {
        String token = buildTokenWith("customer-001", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET);
        assertEquals("customer-001", provider.extractUserId(token));
    }

    @Test
    void customerToken_expired_isInvalid() {
        String token = buildTokenWith("customer-001", "CUSTOMER", pastDate(3600), CUSTOMER_SECRET);
        assertFalse(provider.isTokenValid(token));
    }

    @Test
    void customerToken_toAuthentication_setsCustomerRole() {
        String token = buildTokenWith("customer-abc", "CUSTOMER", futureDate(3600), CUSTOMER_SECRET);
        UsernamePasswordAuthenticationToken auth = provider.toAuthentication(token);
        assertEquals("customer-abc", auth.getPrincipal());
        assertTrue(auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER")));
    }

    @Test
    void tokenWithWrongSecret_isInvalid() {
        GatewayProperties wrongProps = new GatewayProperties();
        wrongProps.getAuth().setJwtSecret(
                "wrong-secret-key-for-testing-wrong-secret-256-bits-!!!");
        wrongProps.getAuth().setCustomerJwtSecret(
                "wrong-customer-secret-key-for-testing-wrong-256-bits!!");
        HmacJwtAuthProvider wrongProvider = new HmacJwtAuthProvider(wrongProps);

        String token = buildToken("user-123", "MANAGER", futureDate(3600));
        assertFalse(wrongProvider.isTokenValid(token));
    }

    @Test
    void garbageString_isInvalid() {
        assertFalse(provider.isTokenValid("not.a.jwt"));
    }

    @Test
    void nullToken_isInvalid() {
        assertFalse(provider.isTokenValid(null));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String buildToken(String userId, String role, Date expiry) {
        return buildTokenWith(userId, role, expiry, ADMIN_SECRET);
    }

    private String buildTokenWith(String userId, String role, Date expiry, String secret) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String buildTokenWithRoles(String userId, List<String> roles, Date expiry) {
        return Jwts.builder()
                .setSubject(userId)
                .claim("role", roles.get(0))
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(expiry)
                .signWith(Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(ADMIN_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private Date futureDate(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000);
    }

    private Date pastDate(long seconds) {
        return new Date(System.currentTimeMillis() - seconds * 1000);
    }
}
