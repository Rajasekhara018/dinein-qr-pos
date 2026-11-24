package com.heuristiq.uam.permissions.persistance.mongo;

import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.enums.UamEnums.ModuleCategory;
import com.heuristiq.uam.enums.UamEnums.PermissionAction;
import com.heuristiq.uam.enums.UamEnums.ResourceType;
import com.heuristiq.uam.helper.AuditInfo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("permissions")
public class PermissionDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    @Indexed
    private String applicationCode;

    @Indexed
    private ModuleCategory module;

    @Indexed
    private ResourceType resource;

    @Indexed
    private PermissionAction action;

    private String displayName;

    private String description;

    private Boolean active;

    private Boolean systemPermission;

    private Map<String,Object> attributes;

    private AuditInfo audit;
}
