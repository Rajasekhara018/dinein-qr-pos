package com.heuristiq.uam.orgstructure.domain;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentFile;

/**
 * Port to wherever uploaded import files live (the document store), for a department file. The user uploads the file there; the
 * import only ever receives the document id and reads the rows back through this port.
 */
public interface DepartmentFileSource {

    /**
     * Reads and parses the file stored under {@code documentId}.
     *
     * @param callerId the signed-in user, recorded by the document store as the reader
     * @throws OrgStructureException when the file is missing, unreadable, not clean or not a valid department CSV
     */
    DepartmentFile fetchDepartments(String documentId, String callerId);
}
