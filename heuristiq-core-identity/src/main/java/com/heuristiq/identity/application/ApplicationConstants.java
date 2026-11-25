package com.heuristiq.identity.application;

/** Fixed values of the applications feature: field names, messages and the lookup's cache lifetime. */
public final class ApplicationConstants {

    /** Base path of the administrator endpoints. */
    public static final String BASE_PATH = "/api/v1/uam/applications";

    /** Path (under {@link #BASE_PATH}) of the catalogue. */
    public static final String CATALOGUE_PATH = "/catalogue";

    /** Self-service lookup of the session organization's applications (authenticated only, not in the privilege catalogue). */
    public static final String LOOKUP_PATH = "/api/v1/uam/identity/applications";

    /** How long a browser or proxy may keep the lookup response. */
    public static final long LOOKUP_CACHE_SECONDS = 60;

    public static final String FIELD_CODE = "code";
    public static final String FIELD_TYPE = "type";

    public static final String CODE_REQUIRED = "Application code is required";
    public static final String CODE_NOT_IN_CATALOGUE = "must be one of the catalogue codes: ";
    public static final String CODE_IMMUTABLE = "cannot be changed after the application is created";
    public static final String TYPE_FIXED = "is fixed by the application catalogue: ";
    public static final String ALREADY_EXISTS = "Application already exists in this organization: ";
    public static final String NOT_FOUND = "Application not found";
    public static final String NO_SESSION_ORGANIZATION = "Your session is not in an organization";
    public static final String SHARED_FIELDS_FORBIDDEN = "The name and description of an application are shared by every organization "
            + "and can only be changed by a platform administrator";

    /** Refused delete: roles ({@code %1$d}) and users ({@code %2$d}) still hold active access to the application. */
    public static final String IN_USE_BY_ACCESS =
            "This application still has %d roles and %d users with access. Remove their access first.";
    public static final String IN_USE_BY_OTHER_RECORDS =
            "This application is still in use by other records, so it cannot be removed. Remove what uses it first.";
    public static final String REMOVED = "Application removed";

    /** Sort field of the administrator list. */
    public static final String SORT_FIELD = "code";

    private ApplicationConstants() {
    }
}
