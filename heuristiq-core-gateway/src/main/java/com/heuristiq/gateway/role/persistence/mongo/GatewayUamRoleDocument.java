package com.heuristiq.gateway.privilege.persistence.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Read-only projection of UAM's {@code uam_roles} collection — only the fields
 * {@code RolePrivilegeRepository} actually needs.
 */
@Data
@Document(collection = "uam_roles")
public class GatewayUamRoleDocument {
    @Id
    private String id;
    private String code;
    private List<String> permissions;
    private Boolean active;
}
