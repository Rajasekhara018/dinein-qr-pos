package com.railcab.dms.search.projection;

/** The steps of one source generation, as Rider_SearchAPI's projection ingress names them. */
public enum ProjectionPhase {
    /** Takes the lease of the generation. */
    BEGIN,
    /** Up to {@link ProjectionConstants#BATCH_SIZE} records; renews the lease. */
    BATCH,
    /** Removes what this generation did not re-send, and closes it. It does not mark the model ready. */
    COMPLETE,
    /** Marks the model ready for this organization, so its screens stop failing closed. */
    TENANT_COMPLETE
}
