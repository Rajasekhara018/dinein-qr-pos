package com.heuristiq.uam.organization.signup;

/** What an applicant sees of an organization: its code and display name, nothing else (never the internal id, status or counts). */
public record SelfSignupOrganization(String code, String name) {
}
