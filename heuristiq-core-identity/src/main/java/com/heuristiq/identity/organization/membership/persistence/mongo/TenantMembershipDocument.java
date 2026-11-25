package com.heuristiq.identity.organization.membership.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.identity.organization.membership.TenantMembershipStatus;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "tenant_memberships")
@CompoundIndexes({
    @CompoundIndex(name = "uk_tenant_membership", def = "{'tenantId':1,'userId':1}", unique = true),
    @CompoundIndex(name = "idx_tenant_memberships_user", def = "{'userId':1}"),
    @CompoundIndex(name = "idx_tenant_memberships_tenant", def = "{'tenantId':1,'status':1}")
})
public class TenantMembershipDocument {

    @Id
    private String id;

    @NotBlank
    private String tenantId;

    @NotBlank
    private String userId;

    private TenantMembershipStatus status;

    private String employeeId;

    private String addedBy;

    private LocalDateTime joinedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
