package com.heuristiq.uam.orgstructure.domain;

/**
 * Port to the "structure as it was before a publish" snapshot that makes the last publish undoable. A snapshot is taken
 * inside the publish transaction, so it exists exactly when the publish does.
 */
public interface StructureSnapshotStore {

    /**
     * Records the tenant's current structure (units, ladders, positions and the unit tree) for a draft about to be
     * published, and also its assignments and reporting lines when {@code includePeople} (when the draft changes people).
     */
    void capture(String tenantId, String changeSetId, boolean includePeople);

    /** True when a snapshot exists for the draft (it is dropped once rolled back). */
    boolean exists(String changeSetId);

    /** Puts the tenant's live structure back as the snapshot recorded it, undoing everything that draft changed. */
    void restore(String tenantId, String changeSetId);

    /** Forgets the snapshot. */
    void drop(String changeSetId);
}
