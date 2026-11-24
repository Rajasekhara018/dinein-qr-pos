package com.heuristiq.uam.permissions.persistance.jpa;

import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.heuristiq.uam.enums.UamEnums.ModuleCategory;
import com.heuristiq.uam.enums.UamEnums.PermissionAction;
import com.heuristiq.uam.enums.UamEnums.ResourceType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.util.JsonMapAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "permissions", uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"))
public class PermissionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @Column(unique = true)
    private String code;

    private String applicationCode;

    @Enumerated(EnumType.STRING)
    private ModuleCategory module;

    @Enumerated(EnumType.STRING)
    private ResourceType resource;

    @Enumerated(EnumType.STRING)
    private PermissionAction action;

    private String displayName;

    // privilege.json descriptions run past 255 chars -- widened in V4.
    @Column(length = 2048)
    private String description;

    private Boolean active;

    private Boolean systemPermission;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String,Object> attributes;

    @Embedded
    private AuditInfo audit;
}
