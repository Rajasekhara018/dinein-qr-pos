package com.heuristiq.uam.orgstructure.adapter.in;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRow;

/** Response shapes of the import API. */
final class OrgImportResponses {
    private OrgImportResponses() {
    }

    /**
     * A rejected row. Each error is "CODE" or "CODE:detail" (for example {@code DEPARTMENT_UNKNOWN:HR}); the
     * code is stable so the UI can translate it.
     */
    record ImportErrorRow(int rowNo, String employeeCode, String fullName, List<String> errors) {
        static ImportErrorRow of(ImportRow row) {
            return new ImportErrorRow(row.input().rowNo(), row.input().employeeCode(), row.input().fullName(), row.errors());
        }
    }
}
