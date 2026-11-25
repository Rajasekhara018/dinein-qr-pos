package com.heuristiq.identity.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Value types of the bulk department import. The batch itself is the same {@link ImportModels.ImportBatch} as the employee import's. */
public final class DepartmentImportModels {
    private DepartmentImportModels() {
    }

    /** One data row of the uploaded file, exactly as read (blank cells are null). */
    public record DepartmentRowInput(int rowNo, String code, String name, String parentCode, String unitType, String ladder) {
        /** A row of a file without a ladder column. */
        public DepartmentRowInput(int rowNo, String code, String name, String parentCode, String unitType) {
            this(rowNo, code, name, parentCode, unitType, null);
        }
    }

    /** A staged row with the outcome of validating it; {@code errors} is empty for a valid row. */
    public record DepartmentRow(DepartmentRowInput input, String status, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** A staged row as stored, used while applying a confirmed batch. */
    public record StagedDepartmentRow(DepartmentRowInput input, String status) {
    }

    /**
     * A department of the organization as it is when the file is checked.
     *
     * @param parentCode the code of its parent, null for a top-level department
     * @param active     false for an archived department, whose code is still taken
     */
    public record ExistingUnit(String id, String code, String name, String unitType, String parentCode, String ladderId, boolean active) {
    }

    /** What the file is checked against: every department of the organization by code. */
    public record DepartmentReference(Map<String, ExistingUnit> unitsByCode, Map<String, String> ladderIdsByName) {
        /** A reference with no position ladders: every named ladder in the file is unknown. */
        public DepartmentReference(Map<String, ExistingUnit> unitsByCode) {
            this(unitsByCode, Map.of());
        }

        /** The id of the ladder with this name (ignoring case and surrounding blanks), or null when there is none. */
        public String ladderId(String name) {
            return name == null ? null : ladderIdsByName.get(DepartmentRowValidator.ladderKey(name));
        }
    }

    /** A department import batch: the employee import's batch plus how many new departments have no position ladder. */
    public record DepartmentBatch(String id, String status, String documentId, String fileName, int totalRows, int validRows,
                                  int errorRows, int createdCount, int updatedCount, int withoutLadder, String message,
                                  String createdBy, Instant createdAt, Instant appliedAt) {
    }

    /** One department right now, found by code while applying; null fields are blanks. */
    public record UnitState(String id, String name, String unitType, String parentId, String ladderId, boolean active) {
    }

    /** The rows of an uploaded department file, as read. */
    public record DepartmentFile(String documentId, String fileName, List<DepartmentRowInput> rows) {
    }
}
