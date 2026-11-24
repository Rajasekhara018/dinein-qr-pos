package com.heuristiq.uam.dto;

import com.heuristiq.uam.helper.AccessibilityPreference;
import com.heuristiq.uam.helper.DashboardPreference;
import com.heuristiq.uam.helper.NotificationPreference;
import com.heuristiq.uam.helper.RegionalPreference;
import com.heuristiq.uam.helper.UiPreference;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Self-service preferences update — has no {@code userId}/{@code tenantId} fields, unlike
 * {@link UpsertUserPreferenceRequest} (whose {@code @NotBlank userId} would fail validation on a
 * self-service payload that never supplies one). {@link com.heuristiq.uam.userpreference.UserPreferenceProfileController}
 * fills in the caller's own id server-side before delegating to {@code UserPreferenceService}.
 */
@Schema(description = "Self-service preferences update for the caller's own account. The user id is always the JWT subject, never client-supplied.")
public record UpdateMyPreferencesRequest(
        @Schema(description = "Application this preference update is scoped to, if application-specific.") String applicationId,
        @Schema(description = "UI display preferences.") UiPreference ui,
        @Schema(description = "Notification preferences.") NotificationPreference notification,
        @Schema(description = "Dashboard layout preferences.") DashboardPreference dashboard,
        @Schema(description = "Accessibility preferences.") AccessibilityPreference accessibility,
        @Schema(description = "Regional/locale preferences.") RegionalPreference regional
) {
}
