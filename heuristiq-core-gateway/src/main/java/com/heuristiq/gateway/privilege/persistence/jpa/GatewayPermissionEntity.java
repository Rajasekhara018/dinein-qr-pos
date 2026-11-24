package com.heuristiq.gateway.privilege.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.Map;

/**
 * Read-only mapping of UAM's {@code permissions} table -- must mirror UAM's
 * {@code PermissionEntity} column/table names exactly. {@code attributes.privilegeId} is the
 * numeric id {@code PrivilegeBasedAccessFilter} matches against a request's apipath.
 */
@Data
@Entity
@Table(name = "permissions")
public class GatewayPermissionEntity {
    @Id
    private String id;

    private String code;

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> attributes;
}
