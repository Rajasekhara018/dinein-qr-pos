package com.heuristiq.uam.organization;

import java.util.List;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;

/** Fixed values around the organization every account and record belonged to before organizations were separated; its code and name are {@link PlatformOrganizationProperties}. */
public final class DefaultOrganizationConstants {

    /**
     * Id of the default organization where it was created by migration (Postgres): the id the org structure already uses. In
     * Mongo mode the organization gets a generated id, so look it up by its code ({@link PlatformOrganizationProperties#code()}).
     */
    public static final String ID = "default";

    /** Message when the default organization is needed but has not been created. */
    public static final String MISSING_MESSAGE = "The default organization does not exist yet";

    /** Recorded as the one who subscribed the default organization to an application. */
    public static final String SYSTEM_ACTOR = "system";

    /** Users read per page when existing users are adopted into the default organization. */
    public static final int ADOPT_PAGE_SIZE = 200;

    /**
     * Catalogue applications the default organization is given at start-up (row created from the catalogue when missing, then
     * linked). Empty by decision of the product owner: applications are configured manually by an administrator (Access
     * management > Applications), so a fresh database starts with none. Add catalogue codes here only to seed a test or demo
     * environment; an application an administrator removed from the organization is never given back.
     */
    public static final List<String> DEFAULT_ORGANIZATION_APPLICATIONS = List.of();

    private DefaultOrganizationConstants() {
    }
}
