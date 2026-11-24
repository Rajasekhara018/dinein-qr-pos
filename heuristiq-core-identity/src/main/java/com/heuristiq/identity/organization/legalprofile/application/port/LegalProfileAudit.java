package com.heuristiq.uam.organization.legalprofile.application.port;

import java.util.Map;

/** Domain audit of legal-profile changes, with the profile before and after. Never fails the change. */
public interface LegalProfileAudit {

    void profileChanged(String organizationId, String actorUserId, Map<String, Object> before, Map<String, Object> after);
}
