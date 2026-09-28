package com.heuristq.dinein.restaurant;

import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantRequest;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantResponse;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.RestaurantSummary;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.SecureTokens;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Onboards a new tenant. Not merchant-facing self-serve signup: there's no platform-admin login system yet, so this
 * is gated by a shared secret ({@code app.security.platform-admin-key}) meant for the platform operator's own tools,
 * not exposed to browsers. Permitted at {@code SecurityConfig} level (like the payment webhooks) and authenticated
 * here instead, the same pattern this codebase already uses for signature-verified callbacks.
 */
@RestController
@RequestMapping("/api/v1/platform/restaurants")
public class PlatformOnboardingController {

    private static final String KEY_HEADER = "X-Platform-Admin-Key";

    private final RestaurantOnboardingService onboardingService;
    private final String platformAdminKey;

    public PlatformOnboardingController(RestaurantOnboardingService onboardingService, AppProperties properties) {
        this.onboardingService = onboardingService;
        this.platformAdminKey = properties.security().platformAdminKey();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OnboardRestaurantResponse onboard(@RequestHeader(KEY_HEADER) String key,
                                             @Valid @RequestBody OnboardRestaurantRequest request) {
        requireValidKey(key);
        return onboardingService.onboard(request);
    }

    /** Every restaurant on the platform with its owner account(s) — the platform operator's own view. */
    @GetMapping
    public List<RestaurantSummary> list(@RequestHeader(KEY_HEADER) String key) {
        requireValidKey(key);
        return onboardingService.listRestaurants();
    }

    private void requireValidKey(String key) {
        if (platformAdminKey == null || platformAdminKey.isBlank()
                || !SecureTokens.constantTimeEquals(key, platformAdminKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid platform admin key");
        }
    }
}
