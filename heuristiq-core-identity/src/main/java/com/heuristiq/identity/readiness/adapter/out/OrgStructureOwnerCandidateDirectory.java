package com.heuristiq.uam.readiness.adapter.out;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;

import com.heuristiq.uam.orgstructure.application.OrgStructureUseCase;
import com.heuristiq.uam.readiness.application.port.OwnerCandidate;
import com.heuristiq.uam.readiness.application.port.OwnerCandidateDirectory;

/**
 * The owner candidates are the department heads of the organization builder (active departments whose head has an account),
 * the same lookup the wallet uses. The organization builder only exists in PostgreSQL mode; without it there are no
 * candidates, so no owner can be chosen.
 */
public class OrgStructureOwnerCandidateDirectory implements OwnerCandidateDirectory {

    private final ObjectProvider<OrgStructureUseCase> structure;

    public OrgStructureOwnerCandidateDirectory(ObjectProvider<OrgStructureUseCase> structure) {
        this.structure = structure;
    }

    @Override
    public List<OwnerCandidate> departmentHeads(String tenantId) {
        OrgStructureUseCase useCase = structure.getIfAvailable();
        if (useCase == null || tenantId == null || tenantId.isBlank()) {
            return List.of();
        }
        return useCase.departmentHeads(tenantId).stream()
                .map(head -> new OwnerCandidate(head.headUserId(), head.headName(), head.unitCode(), head.unitName()))
                .toList();
    }
}
