package com.heuristiq.identity.organization.subscription.persistence.jpa;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_applications",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_application", columnNames = {"tenant_id", "application_code"}),
        indexes = @Index(name = "idx_tenant_applications_tenant", columnList = "tenant_id,status"))
public class TenantApplicationEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "application_code", nullable = false, length = 100)
    private String applicationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantApplicationStatus status;

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Size(max = 500)
    @Column(length = 500)
    private String note;

    @Column(name = "subscribed_by", length = 100)
    private String subscribedBy;

    @Column(name = "subscribed_at")
    private LocalDateTime subscribedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** User ids of the owner team, comma separated (see {@code OwnerTeamCodec}). */
    @Convert(converter = OwnerTeamUserIdsConverter.class)
    @Column(name = "owner_team_user_ids", length = 2000)
    private List<String> ownerTeamUserIds;

    @Column(name = "go_live_override_by", length = 100)
    private String goLiveOverrideBy;

    @Column(name = "go_live_override_at")
    private LocalDateTime goLiveOverrideAt;

    @Column(name = "go_live_override_reason", length = 500)
    private String goLiveOverrideReason;

    @Column(name = "go_live_override_steps", length = 1000)
    private String goLiveOverrideSteps;

    @Column(name = "last_blocked_alert_at")
    private LocalDateTime lastBlockedAlertAt;

    @Column(name = "last_blocked_alert_fingerprint", length = 64)
    private String lastBlockedAlertFingerprint;

    @Column(name = "last_refused_alert_at")
    private LocalDateTime lastRefusedAlertAt;

    @Column(name = "last_refused_alert_fingerprint", length = 64)
    private String lastRefusedAlertFingerprint;
}
