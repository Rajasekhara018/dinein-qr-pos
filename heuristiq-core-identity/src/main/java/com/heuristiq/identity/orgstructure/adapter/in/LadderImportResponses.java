package com.heuristiq.uam.orgstructure.adapter.in;

import java.util.List;

import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRow;

/** Response shapes of the ladders import API. */
final class LadderImportResponses {
    private LadderImportResponses() {
    }

    /**
     * A rejected row. Each error is "CODE" or "CODE:detail" (for example {@code DUPLICATE_ROW:3} or {@code RANK_TAKEN:Manager}); the
     * code is stable so the UI can translate it.
     */
    record LadderErrorRow(int rowNo, String ladder, String position, String rank, String managerial, List<String> errors) {
        static LadderErrorRow of(LadderRow row) {
            return new LadderErrorRow(row.input().rowNo(), row.input().ladder(), row.input().position(), row.input().rank(),
                    row.input().managerial(), row.errors());
        }
    }
}
