package com.heuristiq.uam.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Value types of the bulk employee import. */
public final class ImportModels {
    private ImportModels() {
    }

    /** One data row of the uploaded file, exactly as read (blank cells are null). */
    public record ImportRowInput(int rowNo, String employeeCode, String fullName, String email, String phone,
                                 String departmentCode, String positionName, String managerCode, String joinedOn, String isHead) {
        /** A row of a file without the is_head column. */
        public ImportRowInput(int rowNo, String employeeCode, String fullName, String email, String phone, String departmentCode,
                              String positionName, String managerCode, String joinedOn) {
            this(rowNo, employeeCode, fullName, email, phone, departmentCode, positionName, managerCode, joinedOn, null);
        }
    }

    /** A staged row with the outcome of validating it; {@code errors} is empty for a valid row. */
    public record ImportRow(ImportRowInput input, String status, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public record ImportBatch(String id, String status, String documentId, String fileName, int totalRows, int validRows, int errorRows,
                              int createdCount, int updatedCount, String message, String createdBy, Instant createdAt,
                              Instant appliedAt, int headsSet) {
        /** A batch that has set no department heads. */
        public ImportBatch(String id, String status, String documentId, String fileName, int totalRows, int validRows, int errorRows,
                           int createdCount, int updatedCount, String message, String createdBy, Instant createdAt, Instant appliedAt) {
            this(id, status, documentId, fileName, totalRows, validRows, errorRows, createdCount, updatedCount, message, createdBy,
                    createdAt, appliedAt, 0);
        }
    }

    /** A department as the importer needs it: its id and the ladder its positions come from. */
    public record UnitRef(String id, String ladderId) {
    }

    /**
     * What the file is checked against: departments by code, positions by ladder and lower-cased name,
     * employees that already exist, and their current manager (employee code to manager code).
     */
    public record ImportReference(Map<String, UnitRef> unitsByCode,
                                  Map<String, Map<String, String>> positionIdsByLadderAndName,
                                  Set<String> existingEmployeeCodes,
                                  Map<String, String> existingManagerByCode) {
    }

    /** A staged row as stored, used while applying a confirmed batch. */
    public record StagedRow(ImportRowInput input, String status) {
    }
}
