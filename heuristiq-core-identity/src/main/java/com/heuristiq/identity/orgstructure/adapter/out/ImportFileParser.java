package com.heuristiq.uam.orgstructure.adapter.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;

/** Turns an uploaded CSV file into import rows, checking the header; row content is validated later by the domain. */
final class ImportFileParser {

    private ImportFileParser() {
    }

    /** Row numbers count data rows from 1: the header is not counted. */
    static List<ImportRowInput> parse(String csvText) {
        List<List<String>> table = CsvReader.parse(csvText);
        if (table.isEmpty()) {
            throw OrgStructureException.invalid("The file is empty");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = OrgStructureAdapterConstants.REQUIRED_COLUMNS.stream().filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw OrgStructureException.invalid("Missing column(s): " + String.join(", ", missing));
        }
        List<ImportRowInput> rows = new ArrayList<>(table.size() - 1);
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            rows.add(new ImportRowInput(r, cell(cells, columns, OrgStructureAdapterConstants.COL_EMPLOYEE_CODE), cell(cells, columns, OrgStructureAdapterConstants.COL_FULL_NAME),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_EMAIL), cell(cells, columns, OrgStructureAdapterConstants.COL_PHONE),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_DEPARTMENT_CODE), cell(cells, columns, OrgStructureAdapterConstants.COL_POSITION_NAME),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_MANAGER_CODE), cell(cells, columns, OrgStructureAdapterConstants.COL_JOINED_ON),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_IS_HEAD)));
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
