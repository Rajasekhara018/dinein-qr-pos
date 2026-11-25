package com.heuristiq.identity.orgstructure.adapter.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

/**
 * Turns an uploaded ladders CSV (columns ladder, position, rank and optionally managerial) into import rows, checking the header; row
 * content is validated later by the domain. Header cells match without regard to case or surrounding blanks, in any order, and
 * columns this import does not know are ignored.
 */
final class LadderCsvParser {

    private LadderCsvParser() {
    }

    /** Row numbers count data rows from 1: the header is not counted. */
    static List<LadderRowInput> parse(String csvText) {
        List<List<String>> table = CsvReader.parse(csvText);
        if (table.isEmpty()) {
            throw OrgStructureException.invalid("The file is empty");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = OrgStructureAdapterConstants.LADDER_REQUIRED_COLUMNS.stream()
                .filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw OrgStructureException.invalid("Missing column(s): " + String.join(", ", missing));
        }
        List<LadderRowInput> rows = new ArrayList<>(table.size() - 1);
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            rows.add(new LadderRowInput(r, cell(cells, columns, OrgStructureAdapterConstants.COL_LADDER_NAME),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_LADDER_POSITION),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_LADDER_RANK),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_LADDER_MANAGERIAL)));
        }
        return rows;
    }

    private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= cells.size()) {
            return null;
        }
        String value = cells.get(index).trim();
        return value.isEmpty() ? null : value;
    }
}
