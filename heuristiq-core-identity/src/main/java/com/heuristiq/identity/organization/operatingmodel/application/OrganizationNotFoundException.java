package com.heuristiq.uam.organization.operatingmodel.application;

/** The organization asked for does not exist (or the caller may not see it). */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(String message) {
        super(message);
    }
}
