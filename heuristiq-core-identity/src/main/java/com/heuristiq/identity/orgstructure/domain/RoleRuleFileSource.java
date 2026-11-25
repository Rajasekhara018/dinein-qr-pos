package com.heuristiq.identity.orgstructure.domain;

import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleFile;

/**
 * Port to wherever uploaded import files live (the document store), for a roles file. The user uploads the file there; the import only
 * ever receives the document id and reads the rows back through this port.
 */
public interface RoleRuleFileSource {

    /**
     * Reads and parses the file stored under {@code documentId}.
     *
     * @param callerId the signed-in user, recorded by the document store as the reader
     * @throws OrgStructureException when the file is missing, unreadable, not clean or not a valid roles CSV
     */
    RoleRuleFile fetchRoleRules(String documentId, String callerId);
}
