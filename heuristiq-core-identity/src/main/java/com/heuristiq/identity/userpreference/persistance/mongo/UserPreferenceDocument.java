package com.heuristiq.identity.userpreference.persistance.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.helper.AccessibilityPreference;
import com.heuristiq.identity.helper.DashboardPreference;
import com.heuristiq.identity.helper.NotificationPreference;
import com.heuristiq.identity.helper.RegionalPreference;
import com.heuristiq.identity.helper.UiPreference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_preferences")
@CompoundIndexes({
    @CompoundIndex(name = "uk_user_application", def = "{'userId':1,'applicationId':1}", unique = true)
})
public class UserPreferenceDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String userId;

    @Indexed
    private String applicationId;

    private UiPreference ui;

    private NotificationPreference notification;

    private DashboardPreference dashboard;

    private AccessibilityPreference accessibility;

    private RegionalPreference regional;

    @Builder.Default
    private Boolean active = true;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
