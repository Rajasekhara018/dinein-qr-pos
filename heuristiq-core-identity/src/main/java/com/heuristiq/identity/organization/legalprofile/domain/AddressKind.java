package com.heuristiq.uam.organization.legalprofile.domain;

/** What an address of the legal profile is for; each kind appears at most once. */
public enum AddressKind {
    /** The registered (legal) office. */
    REGISTERED,
    /** Where the organization actually operates from. */
    OPERATING
}
