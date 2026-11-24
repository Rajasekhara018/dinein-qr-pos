package com.heuristq.dinein.restaurant;

import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantRequest;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantResponse;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.RestaurantSummary;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
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
 * Onboards a new tenant. Not merchant-facing self-serve signup. Two ways in, checked here rather than in
 * {@code SecurityConfig} (this path is {@code permitAll()} there, like the payment webhooks): a staff JWT whose
 * account has {@code platformAdmin = true} (see {@code StaffUserEntity}/{@code BootstrapOwnerRunner}) -- the
 * normal path once you're logged into the admin panel as the platform operator -- or the shared secret
 * ({@code app.security.platform-admin-key}) for tooling/scripts that aren't logged in at all.
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
    public OnboardRestaurantResponse onboard(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                             @Valid @RequestBody OnboardRestaurantRequest request) {
        requireAccess(key);
        return onboardingService.onboard(request);
    }

    /** Every restaurant on the platform with its owner account(s) — the platform operator's own view. */
    @GetMapping
    public List<RestaurantSummary> list(@RequestHeader(value = KEY_HEADER, required = false) String key) {
        requireAccess(key);
        return onboardingService.listRestaurants();
    }

    private void requireAccess(String key) {
        if (CurrentStaff.find().map(StaffPrincipal::platformAdmin).orElse(false)) {
            return;
        }
        if (platformAdminKey == null || platformAdminKey.isBlank() || key == null
                || !SecureTokens.constantTimeEquals(key, platformAdminKey)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid platform admin key");
        }
    }
}
