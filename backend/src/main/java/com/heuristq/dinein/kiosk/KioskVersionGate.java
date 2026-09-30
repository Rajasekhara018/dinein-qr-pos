package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuses kiosk calls from an app older than {@code app.kiosk.min-version} with 426, so the kiosk can show an
 * "update required" screen instead of misbehaving against a newer backend. Disabled while the property is empty.
 * A request with no {@code X-App-Version} header is let through: only the app sends it, and blocking unknown
 * clients would break tooling for no safety gain.
 */
@Component
public class KioskVersionGate implements HandlerInterceptor {

    public static final String VERSION_HEADER = "X-App-Version";

    private final String minVersion;

    public KioskVersionGate(@Value("${app.kiosk.min-version:}") String minVersion) {
        this.minVersion = minVersion == null ? "" : minVersion.trim();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String actual = request.getHeader(VERSION_HEADER);
        if (!minVersion.isEmpty() && actual != null && isOlder(actual, minVersion)) {
            throw new ApiException(HttpStatus.UPGRADE_REQUIRED, "APP_UPDATE_REQUIRED",
                    "This kiosk app is out of date. Please update it.");
        }
        return true;
    }

    /** Compares dotted numeric versions ("1.2.10" > "1.2.9"); anything unparseable counts as not older. */
    static boolean isOlder(String actual, String minimum) {
        int[] a = parse(actual);
        int[] m = parse(minimum);
        if (a == null || m == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, m.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < m.length ? m[i] : 0;
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        // Flutter reports "1.0.0" for the version name; a "+build" suffix is not part of the comparison.
        String core = version.trim().split("\\+")[0];
        String[] parts = core.split("\\.");
        int[] numbers = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                numbers[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return numbers;
    }
}
