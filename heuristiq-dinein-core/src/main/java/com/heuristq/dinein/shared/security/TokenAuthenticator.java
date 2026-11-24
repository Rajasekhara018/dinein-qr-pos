package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.kiosk.KioskDeviceService;
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

    public static final String KIOSK_AUTHORITY = "ROLE_KIOSK";

    private final JwtTokenService jwtTokenService;
    private final DeviceTokenService deviceTokenService;
    private final KioskDeviceService kioskDeviceService;

    public TokenAuthenticator(JwtTokenService jwtTokenService, DeviceTokenService deviceTokenService,
                              KioskDeviceService kioskDeviceService) {
        this.jwtTokenService = jwtTokenService;
        this.deviceTokenService = deviceTokenService;
        this.kioskDeviceService = kioskDeviceService;
    }

    public Optional<UsernamePasswordAuthenticationToken> authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            return Optional.empty();
        }
        if (token.startsWith(KioskDeviceService.TOKEN_PREFIX)) {
            return kioskDeviceService.authenticate(token).map(p -> new UsernamePasswordAuthenticationToken(
                    p, null, List.of(new SimpleGrantedAuthority(KIOSK_AUTHORITY))));
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
