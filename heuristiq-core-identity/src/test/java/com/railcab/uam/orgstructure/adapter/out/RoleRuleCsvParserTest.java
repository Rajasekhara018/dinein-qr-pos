package com.heuristiq.uam.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;

/** The roles CSV reader: the header rules and how a cell becomes a value, with no document store involved. */
class RoleRuleCsvParserTest {

    @Test
    void readsTheFourColumnsAndNumbersTheDataRowsFromOne() {
        List<RoleRuleRowInput> rows = RoleRuleCsvParser.parse("department_code,ladder,position_name,role_code\nTECH,,,EMPLOYEE\n,Tech,TL,TEAM_LEAD\n");

        assertThat(rows).containsExactly(new RoleRuleRowInput(1, "TECH", null, null, "EMPLOYEE"),
                new RoleRuleRowInput(2, null, "Tech", "TL", "TEAM_LEAD"));
    }

    @Test
    void theHeaderMatchesWithoutRegardToCaseBlanksOrOrderAndExtraColumnsAreIgnored() {
        List<RoleRuleRowInput> rows = RoleRuleCsvParser.parse(" ROLE_CODE , Department_Code ,notes\nEMPLOYEE,TECH,ignored\n");

        assertThat(rows).containsExactly(new RoleRuleRowInput(1, "TECH", null, null, "EMPLOYEE"));
    }

    @Test
    void cellsAreTrimmedBlankOnesBecomeNullAndAShortRowLeavesTheRestBlank() {
        List<RoleRuleRowInput> rows = RoleRuleCsvParser.parse("department_code,ladder,position_name,role_code\n  TECH  ,   ,\nHR,,,\n");

        assertThat(rows).containsExactly(new RoleRuleRowInput(1, "TECH", null, null, null), new RoleRuleRowInput(2, "HR", null, null, null));
    }

    @Test
    void aMissingRoleColumnIsNamedAndAnEmptyFileIsRefused() {
        assertThatThrownBy(() -> RoleRuleCsvParser.parse("department_code,ladder\nTECH,\n")).isInstanceOf(OrgStructureException.class)
                .hasMessage("Missing column(s): role_code");
        assertThatThrownBy(() -> RoleRuleCsvParser.parse("")).isInstanceOf(OrgStructureException.class).hasMessage("The file is empty");
        assertThat(RoleRuleCsvParser.parse("role_code\n")).isEmpty();
    }
}
