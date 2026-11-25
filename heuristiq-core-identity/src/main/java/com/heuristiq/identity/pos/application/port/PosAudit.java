package com.heuristiq.identity.pos.application.port;

import java.util.Map;

/** Records who changed what; {@code before} is null for a creation. */
public interface PosAudit {

    void changed(String tenantId, String actorUserId, String entityType, String entityId, Map<String, Object> before,
            Map<String, Object> after);
}
