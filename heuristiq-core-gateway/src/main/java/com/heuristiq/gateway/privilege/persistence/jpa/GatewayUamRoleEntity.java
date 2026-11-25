package com.heuristiq.gateway.privilege.persistence.jpa;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.List;

/**
 * Read-only mapping of UAM's {@code uam_roles} table (+ its {@code uam_role_permissions}
 * element-collection join table) -- must mirror UAM's {@code UamRoleEntity} column/table names
 * exactly since both services read/write the same schema. UAM owns migrations; the gateway never
 * inserts/updates through this entity.
 */
@Data
@Entity
@Table(name = "uam_roles")
public class GatewayUamRoleEntity {
    @Id
    private String id;

    private String code;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "uam_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code")
    @OrderColumn(name = "idx")
    private List<String> permissions;

    private Boolean active;
}
