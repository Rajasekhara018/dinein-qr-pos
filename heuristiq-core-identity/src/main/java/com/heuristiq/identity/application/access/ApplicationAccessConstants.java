package com.heuristiq.identity.application.access;

import java.util.Set;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.common.UamCommonConstants;

/** Fixed values of the application-access feature. */
final class ApplicationAccessConstants {

    /** The role that may sign in to every registered application without an explicit grant for each. */
    static final String SUPER_ADMIN_ROLE = UamCommonConstants.SUPER_ADMIN_ROLE;

    /** The search slot that holds a user's applications as a comma-separated list of codes. */
    static final String SEARCH_APPLICATIONS_FIELD = "text9";

    /** How many users are read at a time when a role's access changes and its holders are re-indexed. */
    static final int REINDEX_PAGE_SIZE = 200;

    /** Actor recorded on access granted when onboarding is approved. */
    static final String GRANTED_BY = "onboarding-service";

    /** Note recorded on access granted when onboarding is approved. */
    static final String GRANT_NOTE = "Granted when onboarding was approved";

    /** Back-office account types that are never created through application onboarding. */
    /** The back-office account types (one list, kept in AuthConstants): never put through onboarding. */
    static final Set<UamEnums.UserType> PRIVILEGED_TYPES = com.heuristiq.identity.auth.AuthConstants.BACK_OFFICE_USER_TYPES;

    /** Provisioning into an organization that does not exist (404). Argument: the organization id. */
    static final String MSG_ORGANIZATION_NOT_FOUND = "Organization not found: %s";

    /** Provisioning into an organization that is not ACTIVE (409). Arguments: the organization id, its status. */
    static final String MSG_ORGANIZATION_NOT_ACTIVE =
            "Organization %s is %s, so people cannot be onboarded into it";

    /** Provisioning into an organization that does not use the application (409). Arguments: organization id, application code. */
    static final String MSG_APPLICATION_NOT_SUBSCRIBED =
            "Organization %s does not use the application %s; subscribe it to the application before onboarding people into it";

    private ApplicationAccessConstants() {
    }
}
