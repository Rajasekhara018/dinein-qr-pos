package com.heuristiq.identity.organization.operatingmodel.application.port;

import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelPeriod;
import java.time.Instant;
import java.util.List;

/**
 * The stored operating-model changes of each organization, one per organization and start day. Kept apart from the
 * organization record so an edit of the organization (or a stale cached copy of it) can never overwrite them.
 */
public interface OperatingModelScheduleStore {

    /** The organization's changes, oldest first; empty when it never chose a model. */
    List<OperatingModelPeriod> find(String organizationId);

    /**
     * Makes the organization's changes exactly {@code next}, but only if they are still {@code expected} (what the caller
     * read): otherwise nothing is written and an {@code OperatingModelException} of kind CONFLICT is thrown, so two people
     * changing the schedule at once never silently undo each other.
     */
    void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next, String userId, Instant at);
}
