package com.heuristiq.identity.bootstrap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.heuristiq.identity.enums.UamEnums.ModuleCategory;
import com.heuristiq.identity.enums.UamEnums.PermissionAction;
import com.heuristiq.identity.enums.UamEnums.ResourceType;

/**
 * One entry in {@code privilege.json} — deserialized by {@link PermissionCatalogLoader}.
 * {@code privilegeId}/{@code apipath} are the numeric id + route this permission corresponds to,
 * persisted onto {@code Permission.attributes} so the ApiGateway's PrivilegeBasedAccessFilter can
 * resolve them live from the database per request (role -> permission codes -> privilegeId) rather
 * than from any JWT claim -- see the gateway's RolePrivilegeRepository/RolePrivilegeCacheService.
 * Ignores {@code type} (always "API" today, not needed here).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PermissionCatalogEntry(int privilegeId,
                                      @JsonProperty("name") String code,
                                      String apipath,
                                      ModuleCategory module,
                                      @JsonProperty("submodule") ResourceType resource,
                                      @JsonProperty("operation") PermissionAction action,
                                      String description,
                                      String privilegeCode,
                                      String service) {
    public PermissionCatalogEntry(int privilegeId, String code, String apipath, ModuleCategory module,
                                  ResourceType resource, PermissionAction action, String description) {
        this(privilegeId, code, apipath, module, resource, action, description, null, null);
    }
    public String displayName() {
        return code.replace('_', ' ');
    }
}
