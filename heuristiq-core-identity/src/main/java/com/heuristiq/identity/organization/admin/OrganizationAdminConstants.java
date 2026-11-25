package com.heuristiq.identity.organization.admin;

import com.heuristiq.identity.common.UamCommonConstants;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Fixed values of the organization administrator set-up. */
public final class OrganizationAdminConstants {

    /** Added to the organization code to name the organization's administrator role, e.g. ACME_ADMIN. */
    public static final String ADMIN_ROLE_SUFFIX = "_ADMIN";

    /** The code of an organization's administrator role, e.g. ACME_ADMIN for the organization with code acme. */
    public static String adminRoleCode(String organizationCode) {
        return organizationCode.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]", "_") + ADMIN_ROLE_SUFFIX;
    }

    /** Recorded as the one who made the membership. */
    public static final String ACTOR = "organization-setup";

    /** The sign-in form and the password policy require more than 12 characters, so a one-time password is longer than that. */
    public static final int TEMPORARY_PASSWORD_LENGTH = UamCommonConstants.TEMPORARY_PASSWORD_LENGTH;

    /** Organizations read per page by the administrator backfill. */
    public static final int BACKFILL_PAGE_SIZE = 100;

    /** The note recorded on the application access granted to an administrator role. */
    public static final String ACCESS_NOTE = "Organization administrator";

    /**
     * Privileges only the platform may hold: creating or removing organizations, changing or removing applications,
     * the subscription screens, API clients, the permission catalog itself and search re-indexing. Everything else in the
     * catalog goes to an organization's administrator, so they can run every application their organization has. What they
     * can reach is still limited to their own organization by the services, whatever the role says.
     * <p>
     * {@code APPLICATION_CREATE} is deliberately NOT here: an organization's administrator may add an application to their own
     * organization from the fixed application catalogue ({@code ApplicationService.create} links it to the session organization
     * only). {@code ORGANIZATION_UPDATE} lets an administrator edit their own organization through {@code TenantService.update}.
     * {@code ORGANIZATION_LIST} is not here either: an organization's administrator opens the Organizations screen and sees their
     * own organization only ({@code TenantService.list}, and the search index scopes TENANT records to their owner).
     */
    private static final Set<String> PLATFORM_ONLY = Set.of(
            "ORGANIZATION_CREATE", "ORGANIZATION_DELETE", "ORGANIZATION_ADMIN_CREATE",
            "ORGANIZATION_APPLICATION_SUBSCRIBE", "ORGANIZATION_APPLICATION_UPDATE", "ORGANIZATION_APPLICATION_CANCEL",
            "APPLICATION_UPDATE", "APPLICATION_DELETE",
            "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE",
            "UAM_SEARCH_REINDEX_CREATE", "UAM_SEARCH_REINDEX_READ", "MATRIMONY_SEARCH_REINDEX_CREATE", "MATRIMONY_SEARCH_REINDEX_READ",
            "WALLET_ADMIN_APP_CREATE", "WALLET_ADMIN_APP_UPDATE", "WALLET_ADMIN_APP_AVAILABLE_LIST",
            // Approval flows are shared by every organization, so changing one changes it for all of them.
            "WORKFLOW_DEFINITION_DEPLOY", "WORKFLOW_DEFINITION_MIGRATE", "WORKFLOW_DEFINITION_PROPOSE", "WORKFLOW_DEFINITION_PROPOSAL_APPROVE");

    private static final String PLATFORM_ONLY_PREFIX = "CLIENT_";

    /** True for a privilege an organization's administrator does not get. */
    public static boolean isPlatformOnly(String code) {
        return PLATFORM_ONLY.contains(code) || code.startsWith(PLATFORM_ONLY_PREFIX);
    }

    /** What an organization's administrator may do: the whole catalog minus what only the platform may do. */
    public static List<String> administratorPermissions(Collection<String> catalog) {
        return catalog.stream().filter(code -> !isPlatformOnly(code)).distinct().toList();
    }

    private OrganizationAdminConstants() {
    }
}
