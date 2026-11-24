package com.heuristiq.uam.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;

/** The department CSV reader: the header rules and how a cell becomes a value, with no document store involved. */
class DepartmentCsvParserTest {

    @Test
    void readsTheFourColumnsAndNumbersTheDataRowsFromOne() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("code,name,parent_code,unit_type\nROOT,Company,,Company\nHR,Human Resources,ROOT,Department\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "ROOT", "Company", null, "Company"),
                new DepartmentRowInput(2, "HR", "Human Resources", "ROOT", "Department"));
    }

    @Test
    void theHeaderMatchesWithoutRegardToCaseBlanksOrOrderAndExtraColumnsAreIgnored() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse(" Name , CODE ,notes,Parent_Code\nHuman Resources,HR,ignored,ROOT\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", "Human Resources", "ROOT", null));
    }

    @Test
    void parentCodeAndUnitTypeAreOptionalColumns() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("code,name\nHR,Human Resources\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", "Human Resources", null, null));
    }

    @Test
    void cellsAreTrimmedAndBlankOnesBecomeNull() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("code,name,parent_code,unit_type\n  HR  ,  ,   ,\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", null, null, null));
    }

    @Test
    void aShortRowLeavesTheMissingCellsBlank() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("code,name,parent_code,unit_type\nHR,Human Resources\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", "Human Resources", null, null));
    }

    @Test
    void quotedCellsMayHoldCommasAndAByteOrderMarkAndWindowsLineEndingsAreFine() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("﻿code,name\r\nHR,\"Human Resources, India\"\r\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", "Human Resources, India", null, null));
    }

    @Test
    void aMissingRequiredColumnIsNamed() {
        assertThatThrownBy(() -> DepartmentCsvParser.parse("code,parent_code\nHR,\n")).isInstanceOf(OrgStructureException.class)
                .hasMessage("Missing column(s): name");
        assertThatThrownBy(() -> DepartmentCsvParser.parse("department,title\nHR,x\n")).isInstanceOf(OrgStructureException.class)
                .hasMessage("Missing column(s): code, name");
    }

    @Test
    void anEmptyFileIsRefusedAndAHeaderOnlyFileHasNoRows() {
        assertThatThrownBy(() -> DepartmentCsvParser.parse("")).isInstanceOf(OrgStructureException.class).hasMessage("The file is empty");
        assertThatThrownBy(() -> DepartmentCsvParser.parse("\n\n")).isInstanceOf(OrgStructureException.class).hasMessage("The file is empty");
        assertThat(DepartmentCsvParser.parse("code,name\n")).isEmpty();
    }

    @Test
    void theLadderColumnIsOptionalAndBlankCellsAreNull() {
        List<DepartmentRowInput> rows = DepartmentCsvParser.parse("code,name,ladder\nHR,Human Resources,  Tech \nIT,IT,\n");

        assertThat(rows).containsExactly(new DepartmentRowInput(1, "HR", "Human Resources", null, null, "Tech"),
                new DepartmentRowInput(2, "IT", "IT", null, null, null));
    }

    @Test
    void anEmployeeFileIsNotMistakenForADepartmentFile() {
        assertThatThrownBy(() -> DepartmentCsvParser.parse("employee_code,full_name,department_code,position_name\nE1,Asha,HR,TL\n"))
                .isInstanceOf(OrgStructureException.class).hasMessageStartingWith("Missing column(s)");
    }
}
