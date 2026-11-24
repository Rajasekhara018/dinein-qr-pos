package com.heuristiq.notification.adapters.out.integration.uam;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/** Wire constants of NMS's calls to UAM: paths, auth header shape, service-token claims and role codes. */
final class UamClientConstants {

    /** Paths below the configured UAM base URL. */
    static final String USERS_PAGE_PATH = "/users?page={page}&size={size}";
    static final String ORGANIZATION_PATH = "/organizations/{id}";
    /** Several users by id (comma separated), and the most UAM accepts in one call. */
    static final String USERS_BATCH_PATH = "/users/batch?ids={ids}";
    static final int USERS_BATCH_MAX = 100;

    /** Prefix of the {@code Authorization} header value carrying the minted service token. */
    static final String BEARER_PREFIX = "Bearer ";

    /** Subject and lifetime of the internal service token NMS mints for UAM calls. */
    static final String SERVICE_SUBJECT = "nms-broadcast-service";
    static final Duration TOKEN_TTL = Duration.ofMinutes(2);
    static final String CLAIM_ROLES = "roles";
    static final List<String> SERVICE_TOKEN_ROLES = List.of("SUPER_ADMIN");

    /** UAM role codes used to split broadcast segments. */
    static final String ROLE_CUSTOMER = "CUSTOMER";
    static final String ROLE_DRIVER = "DRIVER";

    /**
     * Exactly the role codes UamBootstrapRunner seeds for admin-panel accounts (see
     * UamBootstrapRunner.ADMIN_PANEL_ROLE_PERMISSIONS) plus SUPER_ADMIN -- checking only the
     * literal strings "ADMIN"/"SUPER_ADMIN" would miss MANAGER/ACCOUNTANT/SUPPORT/AUDITOR accounts entirely.
     */
    static final Set<String> ADMIN_PANEL_ROLES = Set.of(
            "SUPER_ADMIN", "MANAGER", "ACCOUNTANT", "SUPPORT", "AUDITOR");

    private UamClientConstants() {
    }
}
