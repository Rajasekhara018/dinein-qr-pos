package com.heuristiq.identity.userpreference;

import java.time.LocalDateTime;

import com.heuristiq.identity.helper.AccessibilityPreference;
import com.heuristiq.identity.helper.DashboardPreference;
import com.heuristiq.identity.helper.NotificationPreference;
import com.heuristiq.identity.helper.RegionalPreference;
import com.heuristiq.identity.helper.UiPreference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.UserPreferenceDocument} /
 * {@code persistence.jpa.UserPreferenceEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreference {

    private String id;

    private String tenantId;

    private String userId;

    /**
     * Null = Global preference.
     */
    private String applicationId;

    private UiPreference ui;

    private NotificationPreference notification;

    private DashboardPreference dashboard;

    private AccessibilityPreference accessibility;

    private RegionalPreference regional;

    @Builder.Default
    private Boolean active = true;

    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
