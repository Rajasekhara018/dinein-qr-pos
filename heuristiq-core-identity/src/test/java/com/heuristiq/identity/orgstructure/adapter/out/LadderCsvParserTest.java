package com.heuristiq.identity.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.orgstructure.domain.LadderImportModels.LadderRowInput;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;

/** The ladders CSV reader: the header rules and how a cell becomes a value, with no document store involved. */
class LadderCsvParserTest {

    @Test
    void readsTheFourColumnsAndNumbersTheDataRowsFromOne() {
        List<LadderRowInput> rows = LadderCsvParser.parse("ladder,position,rank,managerial\nTech,Engineer,3,no\nTech,Lead,6,yes\n");

        assertThat(rows).containsExactly(new LadderRowInput(1, "Tech", "Engineer", "3", "no"), new LadderRowInput(2, "Tech", "Lead", "6", "yes"));
    }

    @Test
    void theHeaderMatchesWithoutRegardToCaseBlanksOrOrderAndExtraColumnsAreIgnored() {
        List<LadderRowInput> rows = LadderCsvParser.parse(" RANK , Position ,notes,LADDER\n3,Engineer,ignored,Tech\n");

        assertThat(rows).containsExactly(new LadderRowInput(1, "Tech", "Engineer", "3", null));
    }

    @Test
    void managerialIsAnOptionalColumnAndBlankCellsAreNull() {
        List<LadderRowInput> rows = LadderCsvParser.parse("ladder,position,rank\n  Tech ,  ,3\nTech,Lead\n");

        assertThat(rows).containsExactly(new LadderRowInput(1, "Tech", null, "3", null), new LadderRowInput(2, "Tech", "Lead", null, null));
    }

    @Test
    void quotedCellsMayHoldCommasAndAByteOrderMarkAndWindowsLineEndingsAreFine() {
        List<LadderRowInput> rows = LadderCsvParser.parse("﻿ladder,position,rank\r\n\"Tech, India\",Engineer,3\r\n");

        assertThat(rows).containsExactly(new LadderRowInput(1, "Tech, India", "Engineer", "3", null));
    }

    @Test
    void aMissingRequiredColumnIsNamed() {
        assertThatThrownBy(() -> LadderCsvParser.parse("ladder,position\nTech,Engineer\n")).isInstanceOf(OrgStructureException.class)
                .hasMessage("Missing column(s): rank");
        assertThatThrownBy(() -> LadderCsvParser.parse("code,name\nHR,x\n")).isInstanceOf(OrgStructureException.class)
                .hasMessage("Missing column(s): ladder, position, rank");
    }

    @Test
    void anEmptyFileIsRefusedAndAHeaderOnlyFileHasNoRows() {
        assertThatThrownBy(() -> LadderCsvParser.parse("")).isInstanceOf(OrgStructureException.class).hasMessage("The file is empty");
        assertThat(LadderCsvParser.parse("ladder,position,rank\n")).isEmpty();
    }
}
