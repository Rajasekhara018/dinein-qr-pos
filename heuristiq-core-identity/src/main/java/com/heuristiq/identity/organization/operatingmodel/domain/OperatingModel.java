package com.heuristiq.identity.organization.operatingmodel.domain;

/** Who operates an organization's sales (see docs/OPERATING_MODEL_DESIGN.md). */
public enum OperatingModel {
    /** The organization runs every sale itself; no franchise is involved. */
    DIRECT,
    /** The franchise covering the place of a sale operates it; uncovered places refuse bookings. */
    FRANCHISE,
    /**
     * Per place: the franchise covering the place of a sale operates it, and the organization operates every sale in a place
     * no franchise covers (no refusal). Services that do not know this value treat it as DIRECT.
     */
    HYBRID
}
