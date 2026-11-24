package com.heuristiq.gateway.auth.provider;

/**
 * What a verified token says about the caller beyond who they are and what roles they hold; carried as the details of
 * the {@code Authentication} so the filter can forward it as headers.
 *
 * @param tenantId the organization of the caller's session (the token's {@code tenantId} claim)
 * @param orgWide  true when the token carries {@code orgWide}: the caller may look across organizations
 */
public record CallerDetails(String tenantId, boolean orgWide, java.util.List<String> applications) {
    public static final String APPLICATIONS_ATTRIBUTE = "railcab.gateway.verifiedApplications";
    public CallerDetails {
        applications = applications == null ? java.util.List.of() : java.util.List.copyOf(applications);
    }
    public CallerDetails(String tenantId, boolean orgWide) { this(tenantId, orgWide, java.util.List.of()); }

    public CallerDetails(String tenantId) {
        this(tenantId, false);
    }
}
