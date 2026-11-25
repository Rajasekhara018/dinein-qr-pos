package com.heuristiq.registry.config;

/** Fixed request paths of the registry's security chain. */
public final class RegistrySecurityConstants {

    /** Eureka client REST API (registration, renewal, cancel); stateless HTTP Basic callers, so no CSRF. */
    public static final String EUREKA_API_PATHS = "/eureka/**";

    /** Actuator endpoints, open so health checks and metric scrapers need no dashboard credentials. */
    public static final String ACTUATOR_PATHS = "/actuator/**";

    /** Javadoc packaged into the jar, public like the other services. */
    public static final String JAVADOC_PATH = "/javadoc";
    public static final String JAVADOC_SUBPATHS = "/javadoc/**";

    private RegistrySecurityConstants() {
    }
}
