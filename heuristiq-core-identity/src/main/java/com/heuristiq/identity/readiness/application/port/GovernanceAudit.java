package com.heuristiq.identity.readiness.application.port;

import java.util.Map;

/** Domain audit events of the go-live guard (who, what, before and after). Best effort: a failed write never fails the action. */
public interface GovernanceAudit {

    /**
     * @param tenantId        organization
     * @param userId          who did it (null for the scheduler)
     * @param entityId        {@code tenantId:applicationCode}
     * @param overridden      true for an override, false for a refused attempt
     * @param before          state before
     * @param after           state after
     * @param remarks         short description
     */
    void record(String tenantId, String userId, String entityId, boolean overridden, Map<String, Object> before,
                Map<String, Object> after, String remarks);
}
