package com.heuristiq.identity.orgstructure.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Value types of the bulk roles import: one row of the file is one role rule of the kind the org builder's login screen makes. */
public final class RoleRuleImportModels {
    private RoleRuleImportModels() {
    }

    /** One data row of the uploaded file, exactly as read (blank cells are null). */
    public record RoleRuleRowInput(int rowNo, String departmentCode, String ladder, String positionName, String roleCode) {
    }

    /** A staged row with the outcome of validating it; {@code errors} is empty for a valid row. */
    public record RoleRuleRow(RoleRuleRowInput input, String status, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /** A staged row as stored, used while applying a confirmed batch. */
    public record StagedRoleRuleRow(RoleRuleRowInput input, String status) {
    }

    /**
     * What the file is checked against: the codes of the organization's active departments, and for each ladder (key: its name without
     * surrounding blanks, lower case, see {@link LadderRowValidator#key}) its positions by the same kind of key.
     */
    public record RoleRuleReference(Set<String> activeUnitCodes, Map<String, Map<String, String>> positionIdsByLadder) {
    }

    /** A roles import batch: how many rules it added and how many already existed. */
    public record RoleRuleBatch(String id, String status, String documentId, String fileName, int totalRows, int validRows, int errorRows,
                                int createdCount, int unchangedCount, String message, String createdBy, Instant createdAt,
                                Instant appliedAt) {
    }

    /** The rows of an uploaded roles file, as read. */
    public record RoleRuleFile(String documentId, String fileName, List<RoleRuleRowInput> rows) {
    }
}
