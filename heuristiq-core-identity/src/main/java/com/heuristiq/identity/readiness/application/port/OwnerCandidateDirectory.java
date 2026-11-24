package com.heuristiq.uam.readiness.application.port;

import java.util.List;

/** The department heads of one organization, from the organization builder. */
public interface OwnerCandidateDirectory {

    /** Active departments of the organization that have a head with an account; empty when the organization builder is not available. */
    List<OwnerCandidate> departmentHeads(String tenantId);
}
