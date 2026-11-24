package com.heuristiq.uam.organization.signup;

/** An eligible organization as the onboarding service needs it: the internal id to provision into, plus code and name. Internal use only. */
public record ResolvedSelfSignupOrganization(String organizationId, String code, String name) {
}
