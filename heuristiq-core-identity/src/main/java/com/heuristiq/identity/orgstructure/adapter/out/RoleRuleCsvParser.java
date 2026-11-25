package com.heuristiq.identity.orgstructure.adapter.out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;

/**
 * Turns an uploaded roles CSV (columns department_code, ladder, position_name, role_code) into import rows, checking the header; row
 * content is validated later by the domain. Header cells match without regard to case or surrounding blanks, in any order, and columns
 * this import does not know are ignored. Only role_code is required in the header: a row may name a department, a position, or both,
 * so the other three columns may be left out when no row needs them.
 */
final class RoleRuleCsvParser {

    private RoleRuleCsvParser() {
    }

    /** Row numbers count data rows from 1: the header is not counted. */
    static List<RoleRuleRowInput> parse(String csvText) {
        List<List<String>> table = CsvReader.parse(csvText);
        if (table.isEmpty()) {
            throw OrgStructureException.invalid("The file is empty");
        }
        Map<String, Integer> columns = new HashMap<>();
        List<String> header = table.get(0);
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = OrgStructureAdapterConstants.ROLE_RULE_REQUIRED_COLUMNS.stream()
                .filter(name -> !columns.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw OrgStructureException.invalid("Missing column(s): " + String.join(", ", missing));
        }
        List<RoleRuleRowInput> rows = new ArrayList<>(table.size() - 1);
        for (int r = 1; r < table.size(); r++) {
            List<String> cells = table.get(r);
            rows.add(new RoleRuleRowInput(r, cell(cells, columns, OrgStructureAdapterConstants.COL_RULE_DEPARTMENT),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_RULE_LADDER),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_RULE_POSITION),
                    cell(cells, columns, OrgStructureAdapterConstants.COL_RULE_ROLE)));
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
