package com.heuristiq.uam.readiness.domain;

/** Phases of the guided setup path, in the order they are shown and worked through (declaration order matters). */
public enum ReadinessPhase {
    /** Who may use the application: the application row, its link to the organization, owner, roles and users. */
    ACCESS,
    /** The support apps every business application relies on (DMS, NMS). */
    SUPPORT_APPS,
    /** Business setup: wallet, onboarding, reports, in dependency order. */
    BUSINESS,
    /** Whether each service the application relies on answered. */
    CONNECTIVITY,
    /** The final review: passes only when every blocking step passes. */
    GO_LIVE
}
