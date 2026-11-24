package com.heuristiq.uam.readiness.application.port;

import java.util.Collection;
import java.util.List;

/** Who can be alerted: platform administrators and the people of one organization's owner team. */
public interface AlertRecipientDirectory {

    /** Active SUPER_ADMIN accounts, at most {@code limit}. */
    List<AlertRecipient> superAdmins(int limit);

    /** The accounts with these ids that are ACTIVE members of the organization (anyone else is silently left out). */
    List<AlertRecipient> activeMembers(String tenantId, Collection<String> userIds);
}
