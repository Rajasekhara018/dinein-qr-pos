package com.heuristiq.identity.orgstructure.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.heuristiq.identity.orgstructure.domain.Models.Change;
import com.heuristiq.identity.orgstructure.domain.Models.ChangeSet;

/** Port to stored drafts and their ordered operations. */
public interface ChangeSetStore {
    ChangeSet create(String tenantId, String actor, String note);

    Optional<ChangeSet> findOpen(String tenantId);

    Optional<ChangeSet> find(String tenantId, String id);

    Change addChange(String changeSetId, ChangeType type, Map<String, Object> payload);

    boolean removeChange(String changeSetId, String changeId);

    List<Change> changes(String changeSetId);

    /** Moves an OPEN draft to a final status; false when its version moved (it was edited or closed meanwhile). */
    boolean close(String tenantId, String id, long expectedVersion, String status, String actor);

    /** The tenant's most recently published draft, if any (rolled-back and discarded drafts do not count). */
    Optional<ChangeSet> latestPublished(String tenantId);

    /** Marks a PUBLISHED draft as rolled back; false when its version moved or it is no longer published. */
    boolean markRolledBack(String tenantId, String id, long expectedVersion);
}
