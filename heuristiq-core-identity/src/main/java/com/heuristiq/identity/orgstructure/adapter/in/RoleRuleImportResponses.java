package com.heuristiq.uam.orgstructure.adapter.in;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;

/** Response shapes of the roles import API. */
final class RoleRuleImportResponses {
    private RoleRuleImportResponses() {
    }

    /**
     * A rejected row. Each error is "CODE" or "CODE:detail" (for example {@code ROLE_UNKNOWN:BOSS} or {@code DUPLICATE_ROW:3}); the code is
     * stable so the UI can translate it.
     */
    record RoleRuleErrorRow(int rowNo, String departmentCode, String ladder, String positionName, String roleCode, List<String> errors) {
        static RoleRuleErrorRow of(RoleRuleRow row) {
            return new RoleRuleErrorRow(row.input().rowNo(), row.input().departmentCode(), row.input().ladder(),
                    row.input().positionName(), row.input().roleCode(), row.errors());
        }
    }
}
