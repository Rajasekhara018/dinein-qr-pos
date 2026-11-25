package com.heuristiq.identity.orgstructure.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.ExistingUnit;

/**
 * Checks every row of a department file against the organization as it is now and against the rest of the file. Pure logic: it reads
 * nothing and changes nothing, so the whole file can be reported on before anything is applied. Messages are stable codes (see
 * {@link DepartmentErrorCode}) the UI can translate; a code with a detail is written {@code CODE:detail}.
 *
 * <p>The rules, in order: each row on its own (code, name, unit type, duplicate code, archived code); then the parent of each row
 * (it must exist, or be defined by another valid row of the file, in any order); then the parent chains (no loops, no more than
 * {@link DepartmentImportConstants#MAX_UNIT_DEPTH} levels). Rejecting a row can make its children invalid (their parent is now
 * rejected), so the parent and chain checks repeat until a round rejects nothing new.
 */
public final class DepartmentRowValidator {

    private DepartmentRowValidator() {
    }

    /** Machine-readable reasons a row can be rejected. */
    public enum DepartmentErrorCode {
        CODE_REQUIRED, CODE_TOO_LONG, CODE_INVALID, CODE_DUPLICATE, CODE_ARCHIVED,
        NAME_REQUIRED, NAME_TOO_LONG, UNIT_TYPE_TOO_LONG,
        PARENT_UNKNOWN, PARENT_REJECTED, PARENT_CYCLE, TOO_DEEP, LADDER_UNKNOWN
    }

    public static List<DepartmentRow> validate(List<DepartmentRowInput> rows, DepartmentReference reference) {
        List<List<String>> errors = new ArrayList<>(rows.size());
        Map<String, Integer> definingIndex = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            errors.add(new ArrayList<>());
            String code = rows.get(i).code();
            if (code != null) {
                definingIndex.putIfAbsent(code, i);
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            checkRow(rows.get(i), errors.get(i), definingIndex, rows, reference);
        }
        for (int round = 0; round < DepartmentImportConstants.MAX_VALIDATION_ROUNDS; round++) {
            if (!checkParents(rows, errors, definingIndex, reference) && !checkChains(rows, errors, definingIndex, reference)) {
                break;
            }
        }
        List<DepartmentRow> out = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            List<String> rowErrors = List.copyOf(errors.get(i));
            out.add(new DepartmentRow(rows.get(i), rowErrors.isEmpty() ? OrgStructureConstants.IMPORT_ROW_VALID
                    : OrgStructureConstants.IMPORT_ROW_INVALID, rowErrors));
        }
        return out;
    }

    /** How a ladder name is compared: ignoring case and surrounding blanks. */
    public static String ladderKey(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * How many departments the file creates that will have no position ladder: new departments (valid rows only) that name none and
     * whose parent chain gives none. An existing department keeps whatever ladder it has, so it is not counted.
     */
    public static int countWithoutLadder(List<DepartmentRow> rows, DepartmentReference reference) {
        Map<String, DepartmentRowInput> valid = new HashMap<>();
        for (DepartmentRow row : rows) {
            if (row.valid()) {
                valid.putIfAbsent(row.input().code(), row.input());
            }
        }
        Map<String, String> memo = new HashMap<>();
        int count = 0;
        for (DepartmentRowInput input : valid.values()) {
            if (!reference.unitsByCode().containsKey(input.code()) && effectiveLadder(input.code(), valid, reference, memo) == null) {
                count++;
            }
        }
        return count;
    }

    /** The ladder a valid row's department will have once the file is applied (null for none); parents are resolved first. */
    private static String effectiveLadder(String code, Map<String, DepartmentRowInput> valid, DepartmentReference reference,
                                          Map<String, String> memo) {
        if (memo.containsKey(code)) {
            return memo.get(code);
        }
        memo.put(code, null);   // guards the walk; valid rows have no loops
        DepartmentRowInput row = valid.get(code);
        ExistingUnit existing = reference.unitsByCode().get(code);
        String ladder;
        if (row != null && row.ladder() != null) {
            ladder = reference.ladderId(row.ladder());
        } else if (existing != null) {
            ladder = existing.ladderId();
        } else if (row == null || row.parentCode() == null) {
            ladder = null;
        } else {
            ladder = effectiveLadder(row.parentCode(), valid, reference, memo);
        }
        memo.put(code, ladder);
        return ladder;
    }

    // ---- one row on its own -------------------------------------------------------------------------

    private static void checkRow(DepartmentRowInput row, List<String> errors, Map<String, Integer> definingIndex,
                                 List<DepartmentRowInput> rows, DepartmentReference reference) {
        String code = row.code();
        if (code == null) {
            errors.add(DepartmentErrorCode.CODE_REQUIRED.name());
        } else {
            if (code.length() > OrgStructureConstants.IMPORT_MAX_CODE_LENGTH) {
                errors.add(DepartmentErrorCode.CODE_TOO_LONG.name());
            } else if (!DepartmentImportConstants.CODE.matcher(code).matches()) {
                errors.add(DepartmentErrorCode.CODE_INVALID.name());
            }
            int first = definingIndex.get(code);
            if (rows.get(first).rowNo() != row.rowNo()) {
                errors.add(DepartmentErrorCode.CODE_DUPLICATE.name() + sep() + rows.get(first).rowNo());
            }
            ExistingUnit existing = reference.unitsByCode().get(code);
            if (existing != null && !existing.active()) {
                errors.add(DepartmentErrorCode.CODE_ARCHIVED.name() + sep() + code);
            }
        }
        if (row.name() == null) {
            errors.add(DepartmentErrorCode.NAME_REQUIRED.name());
        } else if (row.name().length() > OrgStructureConstants.IMPORT_MAX_NAME_LENGTH) {
            errors.add(DepartmentErrorCode.NAME_TOO_LONG.name());
        }
        if (row.unitType() != null && row.unitType().length() > DepartmentImportConstants.MAX_UNIT_TYPE_LENGTH) {
            errors.add(DepartmentErrorCode.UNIT_TYPE_TOO_LONG.name());
        }
        if (row.ladder() != null && reference.ladderId(row.ladder()) == null) {
            errors.add(DepartmentErrorCode.LADDER_UNKNOWN.name() + sep() + row.ladder());
        }
    }

    // ---- the parent of each row ---------------------------------------------------------------------

    /** Rejects rows whose parent cannot be used; true when it rejected anything (the caller then runs another round). */
    private static boolean checkParents(List<DepartmentRowInput> rows, List<List<String>> errors, Map<String, Integer> definingIndex,
                                        DepartmentReference reference) {
        boolean rejected = false;
        for (int i = 0; i < rows.size(); i++) {
            DepartmentRowInput row = rows.get(i);
            String parent = row.parentCode();
            if (!errors.get(i).isEmpty() || parent == null) {
                continue;
            }
            String problem = null;
            if (parent.equals(row.code())) {
                problem = DepartmentErrorCode.PARENT_CYCLE.name();
            } else if (definingIndex.containsKey(parent)) {
                int p = definingIndex.get(parent);
                if (!errors.get(p).isEmpty()) {
                    problem = DepartmentErrorCode.PARENT_REJECTED.name() + sep() + parent + sep() + rows.get(p).rowNo();
                }
            } else {
                ExistingUnit existing = reference.unitsByCode().get(parent);
                if (existing == null || !existing.active()) {
                    problem = DepartmentErrorCode.PARENT_UNKNOWN.name() + sep() + parent;
                }
            }
            if (problem != null) {
                errors.get(i).add(problem);
                rejected = true;
            }
        }
        return rejected;
    }

    // ---- the chain above each row -------------------------------------------------------------------

    /**
     * Follows each remaining row up its parent chain as it will be once the file is applied, and rejects the rows whose chain loops
     * or is too deep; true when it rejected anything.
     */
    private static boolean checkChains(List<DepartmentRowInput> rows, List<List<String>> errors, Map<String, Integer> definingIndex,
                                       DepartmentReference reference) {
        Map<String, String> parentOf = new HashMap<>();
        for (ExistingUnit unit : reference.unitsByCode().values()) {
            parentOf.put(unit.code(), unit.parentCode());
        }
        for (int i = 0; i < rows.size(); i++) {
            DepartmentRowInput row = rows.get(i);
            if (errors.get(i).isEmpty() && definingIndex.get(row.code()) == i && row.parentCode() != null) {
                parentOf.put(row.code(), row.parentCode());   // a blank parent_code leaves an existing parent as it is
            } else if (errors.get(i).isEmpty() && definingIndex.get(row.code()) == i && !parentOf.containsKey(row.code())) {
                parentOf.put(row.code(), null);               // a new top-level department
            }
        }
        boolean rejected = false;
        for (int i = 0; i < rows.size(); i++) {
            DepartmentRowInput row = rows.get(i);
            if (!errors.get(i).isEmpty()) {
                continue;
            }
            String problem = chainProblem(row.code(), parentOf);
            if (problem != null) {
                errors.get(i).add(problem);
                rejected = true;
            }
        }
        return rejected;
    }

    /** The error of the chain above {@code code}, or null when it ends at a top-level department within the depth limit. */
    private static String chainProblem(String code, Map<String, String> parentOf) {
        Set<String> seen = new HashSet<>();
        seen.add(code);
        int depth = 1;
        String next = parentOf.get(code);
        while (next != null) {
            if (!seen.add(next)) {
                return DepartmentErrorCode.PARENT_CYCLE.name();
            }
            depth++;
            if (depth > DepartmentImportConstants.MAX_UNIT_DEPTH) {
                return DepartmentErrorCode.TOO_DEEP.name() + sep() + DepartmentImportConstants.MAX_UNIT_DEPTH;
            }
            next = parentOf.get(next);
        }
        return null;
    }

    private static String sep() {
        return DepartmentImportConstants.ERROR_DETAIL_SEPARATOR;
    }
}
