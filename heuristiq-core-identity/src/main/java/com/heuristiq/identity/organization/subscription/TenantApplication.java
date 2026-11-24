package com.heuristiq.uam.organization.subscription;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model: an organization's (tenant's) use of a registered application. Applications belong to the platform;
 * this link says which organizations may use which. See {@code persistence.mongo.TenantApplicationDocument} /
 * {@code persistence.jpa.TenantApplicationEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantApplication {

    private String id;

    private String tenantId;

    /** Code of the registered application, stored upper-case. */
    private String applicationCode;

    @Builder.Default
    private TenantApplicationStatus status = TenantApplicationStatus.ACTIVE;

    /** Most members of this organization that may use the application; null means no limit of its own. */
    private Integer maxUsers;

    /** Last day the organization may use the application; null means no end date. */
    private LocalDate validUntil;

    private String note;

    private String subscribedBy;

    private LocalDateTime subscribedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    /**
     * The owner team of the application for this organization: user ids of department heads chosen in the organization
     * builder. Empty means no owner. (The old free-text owner on the shared application row is not used for this.)
     */
    private List<String> ownerTeamUserIds;

    /** Set when a SUPER_ADMIN switched the application on while blocking readiness steps failed. */
    private String goLiveOverrideBy;

    private LocalDateTime goLiveOverrideAt;

    /** Why the override was given (stored as typed, within the configured length limits). */
    private String goLiveOverrideReason;

    /** Ids of the blocking steps that were failing when the override was given, comma separated. */
    private String goLiveOverrideSteps;

    /** When the "application not ready" alert for the scheduled re-check was last sent, and for which blocking set. */
    private LocalDateTime lastBlockedAlertAt;

    private String lastBlockedAlertFingerprint;

    /** The same for the "go-live refused" alert. */
    private LocalDateTime lastRefusedAlertAt;

    private String lastRefusedAlertFingerprint;
}
