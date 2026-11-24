package com.heuristiq.uam.orgstructure.adapter.in;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRow;

/** Response shapes of the department import API. */
final class DepartmentImportResponses {
    private DepartmentImportResponses() {
    }

    /**
     * A rejected row. Each error is "CODE" or "CODE:detail" (for example {@code PARENT_UNKNOWN:EXEC}, or
     * {@code PARENT_REJECTED:EXEC:3} for a parent rejected on row 3); the code is stable so the UI can translate it.
     */
    record DepartmentErrorRow(int rowNo, String code, String name, String parentCode, String unitType, String ladder,
                              List<String> errors) {
        static DepartmentErrorRow of(DepartmentRow row) {
            return new DepartmentErrorRow(row.input().rowNo(), row.input().code(), row.input().name(), row.input().parentCode(),
                    row.input().unitType(), row.input().ladder(), row.errors());
        }
    }
}
