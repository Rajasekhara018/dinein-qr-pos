package com.heuristiq.identity.organization.operatingmodel.application.port;

import java.util.List;
import java.util.Map;

/** Domain audit of operating-model changes, with the schedule before and after. Never fails the change. */
public interface OperatingModelAudit {

    void scheduleChanged(String organizationId, String actorUserId, List<Map<String, String>> before, List<Map<String, String>> after);
}
