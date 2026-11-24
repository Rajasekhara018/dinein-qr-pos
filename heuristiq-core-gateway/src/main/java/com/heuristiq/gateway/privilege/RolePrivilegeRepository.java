package com.heuristiq.gateway.privilege;

import java.util.Set;

/**
 * Read-only access to UAM's role/permission data — resolves a role's CURRENT set of privilege
 * numbers straight from the database, so authorization always reflects whatever a SUPER_ADMIN has
 * assigned that role right now, not a login-time snapshot. Two implementations, selected by
 * {@code railcab.gateway.persistence.type} (mongo default, mysql alternate) exactly like UAM's own
 * domain repositories:
 * <ul>
 *   <li>{@code repository.mongo.RolePrivilegeMongoRepositoryAdapter}</li>
 *   <li>{@code repository.jpa.RolePrivilegeJpaRepositoryAdapter}</li>
 * </ul>
 */
public interface RolePrivilegeRepository {

    /** Resolves {@code roleCode} -> its permission codes -> each permission's numeric privilegeId. */
    Set<Integer> resolvePrivilegeIds(String roleCode);
}
