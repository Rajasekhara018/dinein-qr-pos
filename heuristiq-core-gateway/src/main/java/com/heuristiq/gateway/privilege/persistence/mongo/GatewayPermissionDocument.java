package com.heuristiq.gateway.privilege.persistence.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * Read-only projection of UAM's {@code permissions} collection. {@code attributes.privilegeId}
 * is the numeric id {@code PrivilegeBasedAccessFilter} matches against a request's apipath.
 */
@Data
@Document("permissions")
public class GatewayPermissionDocument {
    @Id
    private String id;
    private String code;
    private Map<String, Object> attributes;
}
