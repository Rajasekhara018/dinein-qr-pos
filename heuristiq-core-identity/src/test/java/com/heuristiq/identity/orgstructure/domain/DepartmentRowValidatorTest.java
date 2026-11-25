package com.heuristiq.identity.orgstructure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.ExistingUnit;

/** Every rule of the department file check, on its own and in combination, with no database. */
class DepartmentRowValidatorTest {

    private static DepartmentRowInput row(int no, String code, String name, String parent) {
        return new DepartmentRowInput(no, code, name, parent, null);
    }

    private static ExistingUnit unit(String code, String parent, boolean active) {
        return new ExistingUnit("id-" + code, code, "Existing " + code, null, parent, null, active);
    }

    private static DepartmentReference reference(ExistingUnit... units) {
        Map<String, ExistingUnit> byCode = new HashMap<>();
        for (ExistingUnit u : units) {
            byCode.put(u.code(), u);
        }
        return new DepartmentReference(byCode);
    }

    private static List<DepartmentRow> validate(DepartmentReference reference, DepartmentRowInput... rows) {
        return DepartmentRowValidator.validate(List.of(rows), reference);
    }

    private static List<String> errorsOf(List<DepartmentRow> rows, int rowNo) {
        return rows.stream().filter(r -> r.input().rowNo() == rowNo).findFirst().orElseThrow().errors();
    }

    // ---- the ladder column --------------------------------------------------------------------------

    private static DepartmentRowInput rowWithLadder(int no, String code, String parent, String ladder) {
        return new DepartmentRowInput(no, code, "Name " + code, parent, null, ladder);
    }

    private static ExistingUnit unitWithLadder(String code, String ladderId) {
        return new ExistingUnit("id-" + code, code, "Existing " + code, null, null, ladderId, true);
    }

    private static DepartmentReference referenceWithLadders(Map<String, String> ladders, ExistingUnit... units) {
        Map<String, ExistingUnit> byCode = new HashMap<>();
        for (ExistingUnit u : units) {
            byCode.put(u.code(), u);
        }
        return new DepartmentReference(byCode, ladders);
    }

    @Test
    void aLadderIsMatchedByNameIgnoringCaseAndSurroundingBlanks() {
        DepartmentReference reference = referenceWithLadders(Map.of("tech", "l-tech"));

        List<DepartmentRow> out = validate(reference, rowWithLadder(1, "A", null, "Tech"), rowWithLadder(2, "B", null, "  TECH "));

        assertThat(out).allMatch(DepartmentRow::valid);
        assertThat(reference.ladderId(" Tech ")).isEqualTo("l-tech");
        assertThat(reference.ladderId(null)).isNull();
    }

    @Test
    void aLadderThatDoesNotExistRejectsTheRowWithItsNameAndABlankLadderIsFine() {
        DepartmentReference reference = referenceWithLadders(Map.of("tech", "l-tech"));

        List<DepartmentRow> out = validate(reference, rowWithLadder(1, "A", null, "Sales"), rowWithLadder(2, "B", null, null));

        assertThat(errorsOf(out, 1)).containsExactly("LADDER_UNKNOWN:Sales");
        assertThat(errorsOf(out, 2)).isEmpty();
    }

    @Test
    void withoutAnyLadderInTheOrganizationAnyNamedLadderIsUnknown() {
        List<DepartmentRow> out = validate(reference(), rowWithLadder(1, "A", null, "Tech"));

        assertThat(errorsOf(out, 1)).containsExactly("LADDER_UNKNOWN:Tech");
    }

    @Test
    void aRowWhoseLadderIsUnknownMakesItsChildrenRejectedToo() {
        List<DepartmentRow> out = validate(referenceWithLadders(Map.of()), rowWithLadder(1, "A", null, "Nope"),
                rowWithLadder(2, "B", "A", null));

        assertThat(errorsOf(out, 2)).containsExactly("PARENT_REJECTED:A:1");
    }

    @Test
    void newDepartmentsWithNoLadderAnywhereAreCounted() {
        DepartmentReference reference = referenceWithLadders(Map.of("tech", "l-tech"), unitWithLadder("EXT", "l-tech"),
                unitWithLadder("PLAIN", null));
        List<DepartmentRow> rows = validate(reference,
                rowWithLadder(1, "ROOT", null, null),            // new, no ladder, no parent: counted
                rowWithLadder(2, "KID", "ROOT", null),           // inherits ROOT's, which is none: counted
                rowWithLadder(3, "NAMED", null, "Tech"),         // its own ladder
                rowWithLadder(4, "UNDER_NAMED", "NAMED", null),  // from a parent row of the file
                rowWithLadder(5, "UNDER_EXT", "EXT", null),      // from an existing parent
                rowWithLadder(6, "UNDER_PLAIN", "PLAIN", null),  // existing parent without one: counted
                rowWithLadder(7, "PLAIN", null, null),           // existing, left alone: not counted
                rowWithLadder(8, "BAD", null, "Nope"));          // rejected: not counted

        assertThat(DepartmentRowValidator.countWithoutLadder(rows, reference)).isEqualTo(3);
    }

    @Test
    void aLadderGivenToAnExistingParentInTheFileReachesItsNewChildrenInAnyRowOrder() {
        DepartmentReference reference = referenceWithLadders(Map.of("tech", "l-tech"), unitWithLadder("PLAIN", null));
        List<DepartmentRow> rows = validate(reference, rowWithLadder(1, "CHILD", "PLAIN", null), rowWithLadder(2, "PLAIN", null, "Tech"));

        assertThat(rows).allMatch(DepartmentRow::valid);
        assertThat(DepartmentRowValidator.countWithoutLadder(rows, reference)).isZero();
    }

    // ---- a row on its own -------------------------------------------------------------------------

    @Test
    void aWellFormedFileIsValidInAnyOrderOfParentAndChild() {
        List<DepartmentRow> out = validate(reference(),
                row(1, "TEAM", "Team", "DEPT"), row(2, "DEPT", "Department", "ROOT"), row(3, "ROOT", "Company", null));

        assertThat(out).allMatch(DepartmentRow::valid);
        assertThat(out).extracting(DepartmentRow::status).containsOnly(OrgStructureConstants.IMPORT_ROW_VALID);
        assertThat(out).extracting(r -> r.input().rowNo()).containsExactly(1, 2, 3);
    }

    @Test
    void codeAndNameAreRequiredAndNothingStaysUnsaidAboutTheUnitType() {
        List<DepartmentRow> out = validate(reference(), row(1, null, null, null));

        assertThat(out.get(0).errors()).containsExactly("CODE_REQUIRED", "NAME_REQUIRED");
        assertThat(out.get(0).status()).isEqualTo(OrgStructureConstants.IMPORT_ROW_INVALID);
    }

    @Test
    void aCodeMustUseOnlyLettersDigitsDotUnderscoreAndHyphenAndNotBeTooLong() {
        List<DepartmentRow> out = validate(reference(), row(1, "HR DEPT", "x", null), row(2, "HR/OPS", "x", null),
                row(3, "A".repeat(101), "x", null), row(4, "A".repeat(100), "x", null), row(5, "hr.ops_1-b", "x", null));

        assertThat(errorsOf(out, 1)).containsExactly("CODE_INVALID");
        assertThat(errorsOf(out, 2)).containsExactly("CODE_INVALID");
        assertThat(errorsOf(out, 3)).containsExactly("CODE_TOO_LONG");
        assertThat(errorsOf(out, 4)).isEmpty();
        assertThat(errorsOf(out, 5)).isEmpty();
    }

    @Test
    void aNameOfTwoHundredCharactersIsFineAndOneMoreIsNot() {
        List<DepartmentRow> out = validate(reference(), row(1, "A", "n".repeat(200), null), row(2, "B", "n".repeat(201), null));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("NAME_TOO_LONG");
    }

    @Test
    void theUnitTypeMayBeBlankOrUpToFiftyCharacters() {
        List<DepartmentRow> out = validate(reference(),
                new DepartmentRowInput(1, "A", "A", null, null), new DepartmentRowInput(2, "B", "B", null, "t".repeat(50)),
                new DepartmentRowInput(3, "C", "C", null, "t".repeat(51)));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).isEmpty();
        assertThat(errorsOf(out, 3)).containsExactly("UNIT_TYPE_TOO_LONG");
    }

    @Test
    void aLaterDuplicateCodeIsRejectedAndNamesTheFirstRow() {
        List<DepartmentRow> out = validate(reference(), row(1, "HR", "Human Resources", null), row(2, "IT", "IT", null),
                row(3, "HR", "Human Resources again", null), row(4, "HR", "and again", null));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 3)).containsExactly("CODE_DUPLICATE:1");
        assertThat(errorsOf(out, 4)).containsExactly("CODE_DUPLICATE:1");
    }

    @Test
    void aCodeAnArchivedDepartmentOwnsCannotBeUsed() {
        List<DepartmentRow> out = validate(reference(unit("OLD", null, false)), row(1, "OLD", "Old", null));

        assertThat(errorsOf(out, 1)).containsExactly("CODE_ARCHIVED:OLD");
    }

    // ---- the parent ----------------------------------------------------------------------------

    @Test
    void aParentMayBeAnExistingDepartment() {
        List<DepartmentRow> out = validate(reference(unit("ROOT", null, true)), row(1, "HR", "HR", "ROOT"));

        assertThat(errorsOf(out, 1)).isEmpty();
    }

    @Test
    void anUnknownParentIsRejectedWithItsCode() {
        List<DepartmentRow> out = validate(reference(), row(1, "HR", "HR", "NOPE"));

        assertThat(errorsOf(out, 1)).containsExactly("PARENT_UNKNOWN:NOPE");
    }

    @Test
    void anArchivedDepartmentIsNotAUsableParent() {
        List<DepartmentRow> out = validate(reference(unit("OLD", null, false)), row(1, "HR", "HR", "OLD"));

        assertThat(errorsOf(out, 1)).containsExactly("PARENT_UNKNOWN:OLD");
    }

    @Test
    void aParentThatIsItselfRejectedRejectsItsChildrenAndGrandchildren() {
        List<DepartmentRow> out = validate(reference(),
                row(1, "BAD CODE", "Bad", null), row(2, "BAD", "Child of nothing known", "MISSING"),
                row(3, "KID", "Kid", "BAD"), row(4, "GRANDKID", "Grandkid", "KID"));

        assertThat(errorsOf(out, 2)).containsExactly("PARENT_UNKNOWN:MISSING");
        assertThat(errorsOf(out, 3)).containsExactly("PARENT_REJECTED:BAD:2");
        assertThat(errorsOf(out, 4)).containsExactly("PARENT_REJECTED:KID:3");
    }

    @Test
    void theRejectedRowsDoNotStopTheOthers() {
        List<DepartmentRow> out = validate(reference(), row(1, "OK1", "One", null), row(2, "BAD", "Two", "MISSING"), row(3, "OK2", "Three", "OK1"));

        assertThat(out).extracting(DepartmentRow::valid).containsExactly(true, false, true);
    }

    // ---- loops ---------------------------------------------------------------------------------

    @Test
    void aDepartmentCannotBeItsOwnParent() {
        List<DepartmentRow> out = validate(reference(), row(1, "A", "A", "A"));

        assertThat(errorsOf(out, 1)).containsExactly("PARENT_CYCLE");
    }

    @Test
    void twoDepartmentsUnderEachOtherBothLoop() {
        List<DepartmentRow> out = validate(reference(), row(1, "A", "A", "B"), row(2, "B", "B", "A"), row(3, "OK", "OK", null));

        assertThat(errorsOf(out, 1)).containsExactly("PARENT_CYCLE");
        assertThat(errorsOf(out, 2)).containsExactly("PARENT_CYCLE");
        assertThat(errorsOf(out, 3)).isEmpty();
    }

    @Test
    void aLongerLoopAndTheDepartmentsHangingOffItAreAllRejected() {
        List<DepartmentRow> out = validate(reference(),
                row(1, "A", "A", "C"), row(2, "B", "B", "A"), row(3, "C", "C", "B"), row(4, "TAIL", "Tail", "C"));

        assertThat(out).extracting(DepartmentRow::valid).containsOnly(false);
        assertThat(errorsOf(out, 4)).containsExactly("PARENT_CYCLE");
    }

    @Test
    void movingAnExistingDepartmentUnderItsOwnDescendantLoopsAgainstTheExistingTree() {
        DepartmentReference existing = reference(unit("ROOT", null, true), unit("MID", "ROOT", true), unit("LEAF", "MID", true));

        List<DepartmentRow> out = validate(existing, row(1, "ROOT", "Root", "LEAF"));

        assertThat(errorsOf(out, 1)).containsExactly("PARENT_CYCLE");
    }

    @Test
    void movingAnExistingDepartmentUnderAnUnrelatedOneIsFine() {
        DepartmentReference existing = reference(unit("ROOT", null, true), unit("MID", "ROOT", true), unit("OTHER", null, true));

        List<DepartmentRow> out = validate(existing, row(1, "MID", "Mid", "OTHER"));

        assertThat(errorsOf(out, 1)).isEmpty();
    }

    @Test
    void aBlankParentOnAnExistingDepartmentLeavesItWhereItIsSoItIsNotALoop() {
        DepartmentReference existing = reference(unit("ROOT", null, true), unit("MID", "ROOT", true));

        List<DepartmentRow> out = validate(existing, row(1, "MID", "Renamed middle", null));

        assertThat(errorsOf(out, 1)).isEmpty();
    }

    @Test
    void aFileThatMovesTwoExistingDepartmentsIntoALoopTogetherIsRejected() {
        DepartmentReference existing = reference(unit("A", null, true), unit("B", null, true));

        List<DepartmentRow> out = validate(existing, row(1, "A", "A", "B"), row(2, "B", "B", "A"));

        assertThat(out).extracting(DepartmentRow::valid).containsOnly(false);
    }

    // ---- depth -----------------------------------------------------------------------------------

    private static List<DepartmentRowInput> chain(int length) {
        List<DepartmentRowInput> rows = new ArrayList<>();
        for (int level = 1; level <= length; level++) {
            rows.add(row(level, "L" + level, "Level " + level, level == 1 ? null : "L" + (level - 1)));
        }
        return rows;
    }

    @Test
    void twelveLevelsAreAllowedAndThirteenIsTooDeep() {
        List<DepartmentRow> out = DepartmentRowValidator.validate(chain(13), reference());

        assertThat(out.subList(0, 12)).allMatch(DepartmentRow::valid);
        assertThat(out.get(12).errors()).containsExactly("TOO_DEEP:12");
    }

    @Test
    void theDepthCountsTheExistingDepartmentsAboveTheFile() {
        ExistingUnit[] tree = new ExistingUnit[11];
        for (int i = 0; i < tree.length; i++) {
            tree[i] = unit("E" + i, i == 0 ? null : "E" + (i - 1), true);
        }

        List<DepartmentRow> out = validate(reference(tree), row(1, "N12", "Level twelve", "E10"), row(2, "N13", "Level thirteen", "N12"));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("TOO_DEEP:12");
    }

    @Test
    void aTooDeepRowRejectsTheRowsBelowIt() {
        List<DepartmentRow> out = DepartmentRowValidator.validate(chain(15), reference());

        assertThat(out.subList(0, 12)).allMatch(DepartmentRow::valid);
        assertThat(out.subList(12, 15)).allMatch(r -> !r.valid());
        assertThat(out.get(13).errors()).containsExactly("TOO_DEEP:12");
    }

    // ---- update versus create --------------------------------------------------------------------

    @Test
    void anExistingCodeIsAnUpdateNotAnError() {
        DepartmentReference existing = reference(unit("HR", null, true));

        List<DepartmentRow> out = validate(existing, row(1, "HR", "Human Resources", null));

        assertThat(errorsOf(out, 1)).isEmpty();
    }

    @Test
    void anEmptyFileHasNoRows() {
        assertThat(DepartmentRowValidator.validate(List.of(), reference())).isEmpty();
    }
}
