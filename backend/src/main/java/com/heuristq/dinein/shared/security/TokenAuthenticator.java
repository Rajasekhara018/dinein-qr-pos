package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.shared.security.jwt.JwtTokenService;
import com.heuristq.dinein.staff.DeviceTokenService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a bearer credential (user JWT or kitchen device token) into a Spring Security authentication.
 * Shared by the HTTP filter and the STOMP CONNECT interceptor.
 */
@Component
public class TokenAuthenticator {

    public static final String DEVICE_TOKEN_PREFIX = "dvc_";
    public static final String PASSWORD_CHANGE_AUTHORITY = "PASSWORD_CHANGE_REQUIRED";

    private final JwtTokenService jwtTokenService;
    private final DeviceTokenService deviceTokenService;

    public TokenAuthenticator(JwtTokenService jwtTokenService, DeviceTokenService deviceTokenService) {
        this.jwtTokenService = jwtTokenService;
        this.deviceTokenService = deviceTokenService;
    }

    public Optional<UsernamePasswordAuthenticationToken> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        Optional<StaffPrincipal> principal = token.startsWith(DEVICE_TOKEN_PREFIX)
                ? deviceTokenService.authenticate(token)
                : jwtTokenService.parse(token);
        return principal.map(p -> new UsernamePasswordAuthenticationToken(p, null, authoritiesFor(p)));
    }

    static List<GrantedAuthority> authoritiesFor(StaffPrincipal principal) {
        if (principal.passwordChangeRequired()) {
            return List.of(new SimpleGrantedAuthority(PASSWORD_CHANGE_AUTHORITY));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    }
}
