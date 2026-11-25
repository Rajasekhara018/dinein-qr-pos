package com.heuristiq.identity.orgstructure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;

/** Every rule of the roles file check, with no database. */
class RoleRuleRowValidatorTest {
    private static final RoleRuleReference REFERENCE = new RoleRuleReference(Set.of("TECH", "HR"),
            Map.of("tech", Map.of("engineer", "p-1", "lead", "p-2"), "empty", Map.of()));
    private static final Set<String> ROLES = Set.of("EMPLOYEE", "TEAM_LEAD");

    private static RoleRuleRowInput row(int no, String dept, String ladder, String position, String role) {
        return new RoleRuleRowInput(no, dept, ladder, position, role);
    }

    private static List<RoleRuleRow> validate(RoleRuleRowInput... rows) {
        return RoleRuleRowValidator.validate(List.of(rows), REFERENCE, ROLES);
    }

    private static List<String> errorsOf(List<RoleRuleRow> rows, int rowNo) {
        return rows.stream().filter(r -> r.input().rowNo() == rowNo).findFirst().orElseThrow().errors();
    }

    @Test
    void aRuleForADepartmentAPositionOrBothIsValidWhateverTheCaseOfTheRoleAndLadder() {
        List<RoleRuleRow> out = validate(row(1, "TECH", null, null, "employee"), row(2, null, " Tech ", "ENGINEER", "Team_Lead"),
                row(3, "HR", "tech", "lead", "EMPLOYEE"));

        assertThat(out).allMatch(RoleRuleRow::valid);
        assertThat(out).extracting(RoleRuleRow::status).containsOnly(OrgStructureConstants.IMPORT_ROW_VALID);
    }

    @Test
    void theRoleIsRequiredAndMustBeOneTheOrganizationMayGrant() {
        List<RoleRuleRow> out = validate(row(1, "TECH", null, null, null), row(2, "TECH", null, null, "BOSS"));

        assertThat(errorsOf(out, 1)).containsExactly("ROLE_REQUIRED");
        assertThat(errorsOf(out, 2)).containsExactly("ROLE_UNKNOWN:BOSS");
    }

    @Test
    void aRuleNeedsADepartmentOrAPosition() {
        List<RoleRuleRow> out = validate(row(1, null, null, null, "EMPLOYEE"), row(2, null, "Tech", null, "EMPLOYEE"));

        assertThat(errorsOf(out, 1)).containsExactly("RULE_TARGET_REQUIRED");
        assertThat(errorsOf(out, 2)).as("a ladder alone targets nobody").containsExactly("RULE_TARGET_REQUIRED");
    }

    @Test
    void anUnknownDepartmentIsRejectedWithItsCode() {
        List<RoleRuleRow> out = validate(row(1, "NOPE", null, null, "EMPLOYEE"));

        assertThat(errorsOf(out, 1)).containsExactly("DEPARTMENT_UNKNOWN:NOPE");
    }

    @Test
    void aPositionNeedsItsLadderAndBothMustExist() {
        List<RoleRuleRow> out = validate(row(1, null, null, "Engineer", "EMPLOYEE"), row(2, null, "Sales", "Engineer", "EMPLOYEE"),
                row(3, null, "Tech", "Nobody", "EMPLOYEE"), row(4, null, "Empty", "Engineer", "EMPLOYEE"));

        assertThat(errorsOf(out, 1)).containsExactly("LADDER_REQUIRED");
        assertThat(errorsOf(out, 2)).containsExactly("LADDER_UNKNOWN:Sales");
        assertThat(errorsOf(out, 3)).containsExactly("POSITION_UNKNOWN:Nobody");
        assertThat(errorsOf(out, 4)).containsExactly("POSITION_UNKNOWN:Engineer");
    }

    @Test
    void theSameRuleTwiceIsRejectedOnTheLaterRowIgnoringCaseAndBlanks() {
        List<RoleRuleRow> out = validate(row(1, "TECH", "Tech", "Engineer", "employee"), row(2, "TECH", " tech", "ENGINEER ", "EMPLOYEE"),
                row(3, "TECH", null, null, "EMPLOYEE"), row(4, "TECH", "Tech", "Lead", "EMPLOYEE"));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("DUPLICATE_ROW:1");
        assertThat(errorsOf(out, 3)).as("a different target is a different rule").isEmpty();
        assertThat(errorsOf(out, 4)).isEmpty();
    }

    @Test
    void severalProblemsOfOneRowAreReportedTogether() {
        List<RoleRuleRow> out = validate(row(1, "NOPE", "Sales", "X", "BOSS"));

        assertThat(errorsOf(out, 1)).containsExactly("ROLE_UNKNOWN:BOSS", "DEPARTMENT_UNKNOWN:NOPE", "LADDER_UNKNOWN:Sales");
    }

    @Test
    void roleKeyTrimsAndUpperCases() {
        assertThat(RoleRuleRowValidator.roleKey(" team_lead ")).isEqualTo("TEAM_LEAD");
    }
}
