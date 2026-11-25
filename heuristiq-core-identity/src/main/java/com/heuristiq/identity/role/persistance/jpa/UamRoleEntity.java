package com.heuristiq.identity.role.persistance.jpa;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.identity.enums.UamEnums.AssignmentType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.util.JsonMapAttributeConverter;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "uam_roles", uniqueConstraints = @UniqueConstraint(name = "uk_uam_role_code", columnNames = "code"))
public class UamRoleEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String tenantId;

    @Column(unique = true)
    private String code;

    private String name;

    private String description;

    private Boolean active;

    private Boolean systemRole;

    private Boolean editable;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "uam_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    @OrderColumn(name = "idx")
    private List<String> permissions;

    private String assignedBy;

    private String assignmentReason;

    @Enumerated(EnumType.STRING)
    private AssignmentType assignmentType;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    @Builder.Default
    private Boolean defaultRole = false;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> attributes;

    @Embedded
    private AuditInfo audit;
}
