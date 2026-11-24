package com.heuristiq.gateway.identity;

/**
 * Read-only access to UAM's own account-active status -- the single source of truth for every
 * role (admin, customer, driver alike) now that identity lives in UAM's {@code uam_users}
 * table/collection. Replaces the earlier per-role AdminUserRepository/CustomerUserRepository/
 * DriverUserRepository, which pointed at AdminAPI's decommissioned {@code admin_users} collection
 * and (for customers) the wrong BookingsAPI collection name entirely.
 *
 * <p>Two implementations, selected by {@code railcab.gateway.persistence.type} (mongo default,
 * mysql via profile) exactly like {@code RolePrivilegeRepository}:
 * <ul>
 *   <li>{@code repository.mongo.UamUserStatusMongoRepositoryAdapter}</li>
 *   <li>{@code repository.jpa.UamUserStatusJpaRepositoryAdapter}</li>
 * </ul>
 */
public interface UamUserStatusRepository {

    /** Returns {@code true} if a UamUser with this id exists and its status is ACTIVE. */
    boolean isActive(String userId);
}
