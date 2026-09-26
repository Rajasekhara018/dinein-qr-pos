package com.heuristq.dinein.auth;

import com.heuristq.dinein.auth.dto.AuthDtos.ChangePasswordRequest;
import com.heuristq.dinein.auth.dto.AuthDtos.DeviceTokenResponse;
import com.heuristq.dinein.auth.dto.AuthDtos.KitchenDeviceRequest;
import com.heuristq.dinein.auth.dto.AuthDtos.LoginRequest;
import com.heuristq.dinein.auth.dto.AuthDtos.MeResponse;
import com.heuristq.dinein.auth.dto.AuthDtos.TokenResponse;
import com.heuristq.dinein.shared.security.CookieFactory;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieFactory cookieFactory;

    public AuthController(AuthService authService, CookieFactory cookieFactory) {
        this.authService = authService;
        this.cookieFactory = cookieFactory;
    }

    /** No-op that lets the SPA obtain the {@code XSRF-TOKEN} cookie before its first cookie-authenticated POST. */
    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(authService.login(request.username(), request.password(), request.pin()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = CookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        return withSession(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = CookieFactory.REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        StaffPrincipal principal = CurrentStaff.require();
        return withSession(authService.changePassword(principal, request.currentPassword(), request.newPassword()));
    }

    @PostMapping("/kitchen-device")
    public DeviceTokenResponse kitchenDevice(@Valid @RequestBody KitchenDeviceRequest request) {
        AuthService.KitchenDeviceSession session = authService.registerKitchenDevice(
                request.username(), request.password(), request.pin(), request.deviceName());
        return new DeviceTokenResponse(session.token(), session.expiresAt(), session.user());
    }

    @GetMapping("/me")
    public MeResponse me() {
        StaffPrincipal principal = CurrentStaff.require();
        return new MeResponse(authService.infoFor(principal.userId()), principal.isDevice());
    }

    private ResponseEntity<TokenResponse> withSession(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookieFactory.refreshCookie(session.refreshToken(), session.refreshTtl()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(session.accessToken(), session.expiresIn(), session.user()));
    }
}
