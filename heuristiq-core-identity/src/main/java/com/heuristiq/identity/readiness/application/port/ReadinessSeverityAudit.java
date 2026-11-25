package com.heuristiq.identity.readiness.application.port;

import java.util.Map;

/** Domain audit events of severity changes (who, which steps, before and after). Best effort: a failed write never fails the action. */
public interface ReadinessSeverityAudit {

    /**
     * @param tenantId organization
     * @param userId   who did it
     * @param entityId {@code tenantId:applicationCode}
     * @param before   effective severity of each step that changed, before
     * @param after    effective severity of each step that changed, after
     * @param remarks  short description
     */
    void record(String tenantId, String userId, String entityId, Map<String, Object> before, Map<String, Object> after,
                String remarks);
}
