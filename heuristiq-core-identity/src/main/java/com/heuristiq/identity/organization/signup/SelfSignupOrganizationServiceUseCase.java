package com.heuristiq.uam.organization.signup;

import java.util.List;

/**
 * Which organizations an applicant without a login may choose for an application. An organization is eligible when it is
 * ACTIVE, holds an active unexpired subscription to the application and has opted in to self-signup.
 */
public interface SelfSignupOrganizationServiceUseCase {

    record ListInput(String applicationCode) {}
    record ListOutput(List<SelfSignupOrganization> organizations) {}
    /** Eligible organizations sorted by name, capped; an unknown or malformed application code gives an empty list. */
    ListOutput list(ListInput input);

    record EligibleOutput(List<ResolvedSelfSignupOrganization> organizations) {}
    /** Same organizations as {@link #list} but with their ids, for the onboarding service (internal use only). */
    EligibleOutput eligible(ListInput input);

    record ResolveInput(String applicationCode, String organizationCode) {}
    record ResolveOutput(ResolvedSelfSignupOrganization organization) {}
    /** The eligible organization with that code (404 when unknown or not eligible, with the same message for both). */
    ResolveOutput resolve(ResolveInput input);
}
