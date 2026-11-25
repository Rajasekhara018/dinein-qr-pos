package com.heuristiq.identity.permissions;

import java.util.Map;

import com.heuristiq.identity.enums.UamEnums.ModuleCategory;
import com.heuristiq.identity.enums.UamEnums.PermissionAction;
import com.heuristiq.identity.enums.UamEnums.ResourceType;
import com.heuristiq.identity.helper.AuditInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.PermissionDocument} / {@code persistence.jpa.PermissionEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    private String id;

    private String code;

    private String applicationCode;

    private ModuleCategory module;

    private ResourceType resource;

    private PermissionAction action;

    private String displayName;

    private String description;

    private Boolean active;

    private Boolean systemPermission;

    private Map<String,Object> attributes;

    private AuditInfo audit;
}
