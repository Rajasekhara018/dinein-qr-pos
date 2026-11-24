package com.heuristiq.uam.orgstructure.domain;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRowInput;

/**
 * Port to wherever uploaded import files live (the document store). The user uploads the file there; the
 * import only ever receives the document id and reads the rows back through this port.
 */
public interface ImportFileSource {

    /** The rows of an uploaded employee file, as read. */
    record ImportFile(String documentId, String fileName, List<ImportRowInput> rows) {
    }

    /**
     * Reads and parses the file stored under {@code documentId}.
     *
     * @param callerId the signed-in user, recorded by the document store as the reader
     * @throws OrgStructureException when the file is missing, unreadable, not clean or not a valid employee CSV
     */
    ImportFile fetch(String documentId, String callerId);
}
