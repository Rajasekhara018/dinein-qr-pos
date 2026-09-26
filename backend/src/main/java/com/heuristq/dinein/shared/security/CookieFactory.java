package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.shared.config.AppProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Central place for cookie names and attributes so every cookie gets HttpOnly/Secure/SameSite consistently. */
@Component
public class CookieFactory {

    public static final String REFRESH_COOKIE = "dinein_rt";
    public static final String REFRESH_COOKIE_PATH = "/api/auth";
    public static final String GUEST_COOKIE = "dinein_gs";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    private final boolean secure;

    public CookieFactory(AppProperties properties) {
        this.secure = properties.cookies().secure();
    }

    public ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true).secure(secure).sameSite("Strict").path(REFRESH_COOKIE_PATH).maxAge(maxAge).build();
    }

    public ResponseCookie clearRefreshCookie() {
        return refreshCookie("", Duration.ZERO);
    }

    public ResponseCookie guestCookie(String value, Duration maxAge) {
        return ResponseCookie.from(GUEST_COOKIE, value)
                .httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(maxAge).build();
    }

    public boolean isSecure() {
        return secure;
    }
}
