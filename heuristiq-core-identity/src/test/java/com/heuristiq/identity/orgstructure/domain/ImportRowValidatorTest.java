package com.heuristiq.identity.orgstructure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportReference;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.identity.orgstructure.domain.ImportModels.UnitRef;

/** The import validator is pure logic, so every rule is checked here without a database. */
class ImportRowValidatorTest {

    private static final ImportReference REFERENCE = new ImportReference(
            Map.of("TECH", new UnitRef("u-tech", "l-tech"), "NOLADDER", new UnitRef("u-x", null)),
            Map.of("l-tech", Map.of("tl", "p-tl", "se", "p-se")),
            Set.of("OLD1"),
            Map.of());

    private static ImportRowInput row(int no, String code, String name, String dept, String position, String manager) {
        return new ImportRowInput(no, code, name, null, null, dept, position, manager, null);
    }

    private static List<String> errorsOf(List<ImportRow> rows, int index) {
        return rows.get(index).errors();
    }

    private static ImportRowInput headRow(int no, String code, String dept, String isHead) {
        return new ImportRowInput(no, code, "Name " + code, null, null, dept, "TL", null, null, isHead);
    }

    @Test
    void isHeadAcceptsYesNoTrueFalseOneZeroInAnyCaseAndBlank() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(headRow(1, "E1", "TECH", "Yes"), headRow(2, "E2", "NOLADDER2", "no"),
                headRow(3, "E3", "OTHER", "TRUE"), headRow(4, "E4", "OTHER2", "false"), headRow(5, "E5", "X", "0"),
                headRow(6, "E6", "Y", null), headRow(7, "E7", "Z", " 1 ")), REFERENCE);

        assertThat(rows).allSatisfy(r -> assertThat(r.errors()).doesNotContain("IS_HEAD_INVALID"));
    }

    @Test
    void anUnknownIsHeadValueIsRejectedWithItsOwnCode() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(headRow(1, "E1", "TECH", "maybe")), REFERENCE);

        assertThat(errorsOf(rows, 0)).containsExactly("IS_HEAD_INVALID");
    }

    @Test
    void aSecondHeadForTheSameDepartmentIsRejectedAndNamesTheFirstRow() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(headRow(1, "E1", "TECH", "yes"), headRow(2, "E2", "TECH", "no"),
                headRow(3, "E3", "TECH", "true")), REFERENCE);

        assertThat(rows.get(0).valid()).isTrue();
        assertThat(rows.get(1).valid()).isTrue();
        assertThat(errorsOf(rows, 2)).containsExactly("HEAD_DUPLICATE:TECH:1");
    }

    @Test
    void headsOfDifferentDepartmentsAndRowsWithoutTheColumnAreUnaffected() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(headRow(1, "E1", "TECH", "yes"), headRow(2, "E2", "NOLADDER", "yes"),
                row(3, "E3", "C", "TECH", "TL", null)), REFERENCE);

        assertThat(errorsOf(rows, 0)).isEmpty();
        assertThat(errorsOf(rows, 1)).doesNotContain("HEAD_DUPLICATE:NOLADDER:2");
        assertThat(errorsOf(rows, 2)).isEmpty();
    }

    @Test
    void anInvalidIsHeadValueDoesNotCountAsTheDepartmentsHead() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(headRow(1, "E1", "TECH", "maybe"), headRow(2, "E2", "TECH", "yes")),
                REFERENCE);

        assertThat(errorsOf(rows, 0)).containsExactly("IS_HEAD_INVALID");
        assertThat(errorsOf(rows, 1)).isEmpty();
    }

    @Test
    void aCompleteRowIsValid() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(row(1, "E1", "Asha", "TECH", "TL", null)), REFERENCE);
        assertThat(rows.get(0).valid()).isTrue();
        assertThat(rows.get(0).status()).isEqualTo(OrgStructureConstants.IMPORT_ROW_VALID);
    }

    @Test
    void positionMatchIgnoresCase() {
        assertThat(ImportRowValidator.validate(List.of(row(1, "E1", "Asha", "TECH", "Se", null)), REFERENCE).get(0).valid())
                .isTrue();
    }

    @Test
    void reportsEveryProblemOfARowTogether() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(new ImportRowInput(1, null, null, "not-an-email", null,
                null, null, null, "13/13/2020")), REFERENCE);
        assertThat(errorsOf(rows, 0)).contains("EMPLOYEE_CODE_REQUIRED", "FULL_NAME_REQUIRED", "EMAIL_INVALID",
                "JOINED_ON_INVALID", "DEPARTMENT_REQUIRED");
    }

    @Test
    void unknownDepartmentAndPositionOutsideTheLadderAreRejected() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(
                row(1, "E1", "A", "NOPE", "TL", null),
                row(2, "E2", "B", "TECH", "Accountant", null),
                row(3, "E3", "C", "NOLADDER", "TL", null)), REFERENCE);
        assertThat(errorsOf(rows, 0)).containsExactly("DEPARTMENT_UNKNOWN:NOPE");
        assertThat(errorsOf(rows, 1)).containsExactly("POSITION_NOT_IN_LADDER:Accountant");
        assertThat(errorsOf(rows, 2)).containsExactly("DEPARTMENT_HAS_NO_LADDER:NOLADDER");
    }

    @Test
    void aRepeatedEmployeeCodeIsRejectedOnTheLaterRowOnly() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(
                row(1, "E1", "A", "TECH", "TL", null),
                row(2, "E1", "B", "TECH", "SE", null)), REFERENCE);
        assertThat(rows.get(0).valid()).isTrue();
        assertThat(errorsOf(rows, 1)).containsExactly("EMPLOYEE_CODE_DUPLICATE:1");
    }

    @Test
    void managerMayBeInTheFileOrAlreadyExistButNotUnknownOrSelf() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(
                row(1, "E1", "A", "TECH", "TL", "OLD1"),
                row(2, "E2", "B", "TECH", "SE", "E1"),
                row(3, "E3", "C", "TECH", "SE", "GHOST"),
                row(4, "E4", "D", "TECH", "SE", "E4")), REFERENCE);
        assertThat(rows.get(0).valid()).isTrue();
        assertThat(rows.get(1).valid()).isTrue();
        assertThat(errorsOf(rows, 2)).containsExactly("MANAGER_UNKNOWN:GHOST");
        assertThat(errorsOf(rows, 3)).containsExactly("MANAGER_IS_SELF");
    }

    @Test
    void aReportingCycleWithinTheFileIsRejectedOnEveryRowOfTheCycle() {
        List<ImportRow> rows = ImportRowValidator.validate(List.of(
                row(1, "A", "A", "TECH", "TL", "B"),
                row(2, "B", "B", "TECH", "SE", "C"),
                row(3, "C", "C", "TECH", "SE", "A"),
                row(4, "D", "D", "TECH", "SE", "A")), REFERENCE);
        assertThat(errorsOf(rows, 0)).containsExactly("MANAGER_CYCLE");
        assertThat(errorsOf(rows, 1)).containsExactly("MANAGER_CYCLE");
        assertThat(errorsOf(rows, 2)).containsExactly("MANAGER_CYCLE");
        assertThat(rows.get(3).valid()).as("D only hangs off the cycle, it is not part of it").isTrue();
    }

    @Test
    void aCycleThroughAnExistingReportingLineIsRejected() {
        ImportReference withLine = new ImportReference(REFERENCE.unitsByCode(), REFERENCE.positionIdsByLadderAndName(),
                Set.of("BOSS", "WORKER"), Map.of("BOSS", "WORKER"));
        // BOSS already reports to WORKER; making WORKER report to BOSS would close the loop.
        List<ImportRow> rows = ImportRowValidator.validate(List.of(row(1, "WORKER", "W", "TECH", "SE", "BOSS")), withLine);
        assertThat(errorsOf(rows, 0)).containsExactly("MANAGER_CYCLE");
    }
}
