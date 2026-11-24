package com.heuristiq.uam.orgstructure.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;

/**
 * Checks every row of a roles file on its own, against the organization and against the rest of the file. Pure logic: it reads nothing
 * and changes nothing. Messages are stable codes (see {@link RoleRuleErrorCode}); a code with a detail is written {@code CODE:detail}.
 *
 * <p>A row is one role rule: the role goes to the employees in the department (and below), in the position of the ladder, or both, exactly
 * like a rule made on the logins screen. The role must be one the organization may grant, which is the check
 * {@code OrgLoginService.createRule} makes. Whether a rule already exists is not an error: applying counts it as unchanged.
 */
public final class RoleRuleRowValidator {

    private RoleRuleRowValidator() {
    }

    /** Machine-readable reasons a row can be rejected. */
    public enum RoleRuleErrorCode {
        ROLE_REQUIRED, ROLE_UNKNOWN, RULE_TARGET_REQUIRED, DEPARTMENT_UNKNOWN, LADDER_REQUIRED, LADDER_UNKNOWN, POSITION_UNKNOWN,
        DUPLICATE_ROW
    }

    /** The role as it is compared and stored: trimmed and upper-cased. */
    public static String roleKey(String roleCode) {
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * @param roleCodes the upper-cased codes of the roles the organization may grant (shared ones and its own)
     */
    public static List<RoleRuleRow> validate(List<RoleRuleRowInput> rows, RoleRuleReference reference, Set<String> roleCodes) {
        Map<String, Integer> firstRowOfRule = new HashMap<>();
        for (RoleRuleRowInput row : rows) {
            if (row.roleCode() != null && hasTarget(row)) {
                firstRowOfRule.putIfAbsent(ruleKey(row), row.rowNo());
            }
        }
        List<RoleRuleRow> out = new ArrayList<>(rows.size());
        for (RoleRuleRowInput row : rows) {
            List<String> errors = new ArrayList<>();
            checkRole(row, roleCodes, errors);
            checkTarget(row, reference, errors);
            if (row.roleCode() != null && hasTarget(row) && firstRowOfRule.get(ruleKey(row)) != row.rowNo()) {
                errors.add(RoleRuleErrorCode.DUPLICATE_ROW.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + firstRowOfRule.get(ruleKey(row)));
            }
            out.add(new RoleRuleRow(row, errors.isEmpty() ? OrgStructureConstants.IMPORT_ROW_VALID : OrgStructureConstants.IMPORT_ROW_INVALID,
                    List.copyOf(errors)));
        }
        return out;
    }

    private static boolean hasTarget(RoleRuleRowInput row) {
        return row.departmentCode() != null || row.positionName() != null;
    }

    private static String ruleKey(RoleRuleRowInput row) {
        return String.join("|", row.departmentCode() == null ? "" : row.departmentCode(),
                row.ladder() == null ? "" : LadderRowValidator.key(row.ladder()),
                row.positionName() == null ? "" : LadderRowValidator.key(row.positionName()), roleKey(row.roleCode()));
    }

    private static void checkRole(RoleRuleRowInput row, Set<String> roleCodes, List<String> errors) {
        if (row.roleCode() == null) {
            errors.add(RoleRuleErrorCode.ROLE_REQUIRED.name());
        } else if (!roleCodes.contains(roleKey(row.roleCode()))) {
            errors.add(RoleRuleErrorCode.ROLE_UNKNOWN.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + row.roleCode());
        }
    }

    private static void checkTarget(RoleRuleRowInput row, RoleRuleReference reference, List<String> errors) {
        if (!hasTarget(row)) {
            errors.add(RoleRuleErrorCode.RULE_TARGET_REQUIRED.name());
        }
        if (row.departmentCode() != null && !reference.activeUnitCodes().contains(row.departmentCode())) {
            errors.add(RoleRuleErrorCode.DEPARTMENT_UNKNOWN.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + row.departmentCode());
        }
        if (row.positionName() != null && row.ladder() == null) {
            errors.add(RoleRuleErrorCode.LADDER_REQUIRED.name());
            return;
        }
        if (row.ladder() == null) {
            return;
        }
        Map<String, String> positions = reference.positionIdsByLadder().get(LadderRowValidator.key(row.ladder()));
        if (positions == null) {
            errors.add(RoleRuleErrorCode.LADDER_UNKNOWN.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + row.ladder());
        } else if (row.positionName() != null && !positions.containsKey(LadderRowValidator.key(row.positionName()))) {
            errors.add(RoleRuleErrorCode.POSITION_UNKNOWN.name() + LadderImportConstants.ERROR_DETAIL_SEPARATOR + row.positionName());
        }
    }
}
