package com.heuristiq.uam.userpreference.persistance.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.helper.AccessibilityPreference;
import com.heuristiq.uam.helper.DashboardPreference;
import com.heuristiq.uam.helper.NotificationPreference;
import com.heuristiq.uam.helper.RegionalPreference;
import com.heuristiq.uam.helper.UiPreference;
import com.heuristiq.uam.util.AccessibilityPreferenceAttributeConverter;
import com.heuristiq.uam.util.DashboardPreferenceAttributeConverter;
import com.heuristiq.uam.util.NotificationPreferenceAttributeConverter;
import com.heuristiq.uam.util.RegionalPreferenceAttributeConverter;
import com.heuristiq.uam.util.UiPreferenceAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_preferences", uniqueConstraints = @UniqueConstraint(name = "uk_user_application", columnNames = {"user_id", "application_id"}))
public class UserPreferenceEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String tenantId;

    private String userId;

    private String applicationId;

    @Convert(converter = UiPreferenceAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private UiPreference ui;

    @Convert(converter = NotificationPreferenceAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private NotificationPreference notification;

    @Convert(converter = DashboardPreferenceAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private DashboardPreference dashboard;

    @Convert(converter = AccessibilityPreferenceAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private AccessibilityPreference accessibility;

    @Convert(converter = RegionalPreferenceAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private RegionalPreference regional;

    @Builder.Default
    private Boolean active = true;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
