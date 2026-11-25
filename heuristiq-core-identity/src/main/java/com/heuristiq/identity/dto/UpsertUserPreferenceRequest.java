package com.heuristiq.identity.dto;

import com.heuristiq.identity.helper.AccessibilityPreference;
import com.heuristiq.identity.helper.DashboardPreference;
import com.heuristiq.identity.helper.NotificationPreference;
import com.heuristiq.identity.helper.RegionalPreference;
import com.heuristiq.identity.helper.UiPreference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Create/update (upsert) payload for a user's preferences on the admin-managed endpoint.")
public record UpsertUserPreferenceRequest(
        @Schema(description = "Tenant this preference record belongs to, if multi-tenant.") String tenantId,
        @Schema(description = "The user id this preference record belongs to.") @NotBlank String userId,
        @Schema(description = "Application this preference record is scoped to, if application-specific.") String applicationId,
        @Schema(description = "UI display preferences.") UiPreference ui,
        @Schema(description = "Notification preferences.") NotificationPreference notification,
        @Schema(description = "Dashboard layout preferences.") DashboardPreference dashboard,
        @Schema(description = "Accessibility preferences.") AccessibilityPreference accessibility,
        @Schema(description = "Regional/locale preferences.") RegionalPreference regional
) {
}
