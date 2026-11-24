package com.heuristiq.uam.organization.subscription.persistence.mongo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.organization.subscription.TenantApplicationStatus;

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
@Document(collection = "tenant_applications")
@CompoundIndexes({
    @CompoundIndex(name = "uk_tenant_application", def = "{'tenantId':1,'applicationCode':1}", unique = true),
    @CompoundIndex(name = "idx_tenant_applications_tenant", def = "{'tenantId':1,'status':1}"),
    @CompoundIndex(name = "idx_tenant_applications_code", def = "{'applicationCode':1,'status':1}")
})
public class TenantApplicationDocument {

    @Id
    private String id;

    @NotBlank
    private String tenantId;

    @NotBlank
    @Size(max = 100)
    private String applicationCode;

    private TenantApplicationStatus status;

    private Integer maxUsers;

    private LocalDate validUntil;

    @Size(max = 500)
    private String note;

    private String subscribedBy;

    private LocalDateTime subscribedAt;

    private String updatedBy;

    private LocalDateTime updatedAt;

    private List<String> ownerTeamUserIds;

    private String goLiveOverrideBy;

    private LocalDateTime goLiveOverrideAt;

    private String goLiveOverrideReason;

    private String goLiveOverrideSteps;

    private LocalDateTime lastBlockedAlertAt;

    private String lastBlockedAlertFingerprint;

    private LocalDateTime lastRefusedAlertAt;

    private String lastRefusedAlertFingerprint;
}
