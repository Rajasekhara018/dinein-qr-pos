package com.heuristiq.uam.organization.membership.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.organization.membership.TenantMembershipStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tenant_memberships",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_membership", columnNames = {"tenant_id", "user_id"}),
        indexes = {
                @Index(name = "idx_tenant_memberships_user", columnList = "user_id"),
                @Index(name = "idx_tenant_memberships_tenant", columnList = "tenant_id,status")
        })
public class TenantMembershipEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @NotBlank
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TenantMembershipStatus status;

    @Column(name = "employee_id", length = 64)
    private String employeeId;

    @Column(name = "added_by", length = 100)
    private String addedBy;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
