package com.heuristiq.uam.organization.membership;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model: one person's membership of one organization (tenant). A person has one account for all of the
 * platform and a membership for each organization they work in, so the same account can be told apart per organization by
 * (tenantId, userId). See {@code persistence.mongo.TenantMembershipDocument} / {@code persistence.jpa.TenantMembershipEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMembership {

    private String id;

    private String tenantId;

    private String userId;

    @Builder.Default
    private TenantMembershipStatus status = TenantMembershipStatus.ACTIVE;

    /** The person's record in the organization's structure (departments and positions), once they have one. */
    private String employeeId;

    private String addedBy;

    private LocalDateTime joinedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
