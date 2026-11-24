package com.heuristiq.uam.orgstructure.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.orgstructure.domain.LadderImportModels.ExistingPosition;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderReference;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRow;
import com.heuristiq.uam.orgstructure.domain.LadderImportModels.LadderRowInput;

/** Every rule of the ladders file check, with no database. */
class LadderRowValidatorTest {

    private static LadderRowInput row(int no, String ladder, String position, String rank, String managerial) {
        return new LadderRowInput(no, ladder, position, rank, managerial);
    }

    private static LadderReference reference(Map<String, Map<String, ExistingPosition>> ladders) {
        return new LadderReference(ladders);
    }

    private static LadderReference techWith(ExistingPosition... positions) {
        Map<String, ExistingPosition> byName = new HashMap<>();
        for (ExistingPosition p : positions) {
            byName.put(LadderRowValidator.key(p.name()), p);
        }
        return reference(Map.of("tech", byName));
    }

    private static List<LadderRow> validate(LadderReference reference, LadderRowInput... rows) {
        return LadderRowValidator.validate(List.of(rows), reference);
    }

    private static List<String> errorsOf(List<LadderRow> rows, int rowNo) {
        return rows.stream().filter(r -> r.input().rowNo() == rowNo).findFirst().orElseThrow().errors();
    }

    @Test
    void aWellFormedFileIsValidForNewAndExistingLaddersAndTheManagerialFlagIsOptional() {
        List<LadderRow> out = validate(techWith(), row(1, "Tech", "Engineer", "3", null), row(2, "Tech", "Lead", "6", "yes"),
                row(3, "Brand new ladder", "Associate", "1", "No"));

        assertThat(out).allMatch(LadderRow::valid);
        assertThat(out).extracting(LadderRow::status).containsOnly(OrgStructureConstants.IMPORT_ROW_VALID);
    }

    @Test
    void theLadderAndThePositionAreRequiredAndLimitedTo200Characters() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, null, null, "1", null), row(2, "L".repeat(201), "P".repeat(201), "2", null),
                row(3, "L".repeat(200), "P".repeat(200), "3", null));

        assertThat(errorsOf(out, 1)).containsExactly("LADDER_REQUIRED", "POSITION_REQUIRED");
        assertThat(errorsOf(out, 2)).containsExactly("LADDER_TOO_LONG", "POSITION_TOO_LONG");
        assertThat(errorsOf(out, 3)).isEmpty();
    }

    @Test
    void theRankMustBeAWholeNumberFromOneTo99() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, "A", "p1", "1", null), row(2, "A", "p2", "99", null),
                row(3, "A", "p3", "0", null), row(4, "A", "p4", "100", null), row(5, "A", "p5", "x", null), row(6, "A", "p6", null, null),
                row(7, "A", "p7", "2.5", null), row(8, "A", "p8", "-3", null), row(9, "A", "p9", " 12 ", null));

        assertThat(out.stream().filter(LadderRow::valid).map(r -> r.input().rowNo())).containsExactly(1, 2, 9);
        for (int rowNo = 3; rowNo <= 8; rowNo++) {
            assertThat(errorsOf(out, rowNo)).containsExactly("RANK_INVALID");
        }
    }

    @Test
    void theManagerialFlagTakesYesNoTrueFalseOneZeroAndRejectsAnythingElse() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, "A", "a", "1", "yes"), row(2, "A", "b", "2", "FALSE"),
                row(3, "A", "c", "3", "1"), row(4, "A", "d", "4", "0"), row(5, "A", "e", "5", "maybe"));

        assertThat(out.subList(0, 4)).allMatch(LadderRow::valid);
        assertThat(errorsOf(out, 5)).containsExactly("MANAGERIAL_INVALID");
    }

    @Test
    void theSamePositionTwiceInOneLadderIsRejectedOnTheLaterRowIgnoringCaseAndBlanks() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, "Tech", "Engineer", "3", null), row(2, " tech ", "ENGINEER", "4", null),
                row(3, "Sales", "Engineer", "3", null));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("DUPLICATE_ROW:1");
        assertThat(errorsOf(out, 3)).as("another ladder may use the same title and rank").isEmpty();
    }

    @Test
    void twoDifferentPositionsOfOneLadderCannotShareARankInTheFile() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, "Tech", "Engineer", "3", null), row(2, "tech", "Developer", "3", null));

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("RANK_DUPLICATE:1");
    }

    @Test
    void aRankAnotherExistingPositionHoldsIsRejectedButTheSamePositionMayKeepOrChangeItsRank() {
        LadderReference reference = techWith(new ExistingPosition("p-1", "Engineer", 3, false), new ExistingPosition("p-2", "Lead", 6, true));

        List<LadderRow> out = validate(reference, row(1, "Tech", "LEAD", "7", "yes"),      // changes its own rank
                row(2, "Tech", "Architect", "3", null),                                     // taken by Engineer
                row(3, "tech", "Principal", "6", null));                                    // taken by Lead

        assertThat(errorsOf(out, 1)).isEmpty();
        assertThat(errorsOf(out, 2)).containsExactly("RANK_TAKEN:Engineer");
        assertThat(errorsOf(out, 3)).containsExactly("RANK_TAKEN:Lead");
        assertThat(validate(reference, row(1, "Tech", "Engineer", "3", null)).get(0).valid()).as("unchanged").isTrue();
    }

    @Test
    void aNewLadderHasNoRanksToCollideWithAndOtherLaddersDoNotCount() {
        LadderReference reference = techWith(new ExistingPosition("p-1", "Engineer", 3, false));

        List<LadderRow> out = validate(reference, row(1, "Sales", "Executive", "3", null));

        assertThat(out.get(0).valid()).isTrue();
    }

    @Test
    void severalProblemsOfOneRowAreReportedTogether() {
        List<LadderRow> out = validate(reference(Map.of()), row(1, "A", "p", "1", null), row(2, "A", "p", "zz", "maybe"));

        assertThat(errorsOf(out, 2)).containsExactly("RANK_INVALID", "MANAGERIAL_INVALID", "DUPLICATE_ROW:1");
    }

    @Test
    void parseRankAndKeyHelpers() {
        assertThat(LadderRowValidator.parseRank("07")).isEqualTo(7);
        assertThat(LadderRowValidator.parseRank("100")).isNull();
        assertThat(LadderRowValidator.key("  Senior ENGINEER ")).isEqualTo("senior engineer");
    }

    @Test
    void importFlagsReadYesNoAndBlank() {
        assertThat(ImportFlags.isValid(null)).isTrue();
        assertThat(ImportFlags.isValid(" Yes ")).isTrue();
        assertThat(ImportFlags.isValid("y")).isFalse();
        assertThat(ImportFlags.isYes(null)).isFalse();
        assertThat(ImportFlags.isYes("TRUE")).isTrue();
        assertThat(ImportFlags.isYes("0")).isFalse();
    }
}
