package com.heuristiq.uam.orgstructure.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;

class ImportFileParserTest {

    @Test
    void readsQuotedCellsWithCommasQuotesAndLineBreaks() {
        List<List<String>> table = CsvReader.parse("a,\"b,1\",\"say \"\"hi\"\"\",\"two\nlines\"\r\nx,y,z,w\r\n");
        assertThat(table).hasSize(2);
        assertThat(table.get(0)).containsExactly("a", "b,1", "say \"hi\"", "two\nlines");
        assertThat(table.get(1)).containsExactly("x", "y", "z", "w");
    }

    @Test
    void skipsBlankLinesAndAByteOrderMark() {
        List<List<String>> table = CsvReader.parse("﻿h1,h2\n\n1,2\n   \n3,4");
        assertThat(table).containsExactly(List.of("h1", "h2"), List.of("1", "2"), List.of("3", "4"));
    }

    @Test
    void mapsColumnsByHeaderNameInAnyOrderAndCase() {
        String csv = "Full_Name, EMPLOYEE_CODE ,position_name,department_code,manager_employee_code\n"
                + "Asha,E1,TL,TECH,\n"
                + "Ravi,E2,SE,TECH,E1\n";
        List<ImportRowInput> rows = ImportFileParser.parse(csv);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).isEqualTo(new ImportRowInput(1, "E1", "Asha", null, null, "TECH", "TL", null, null));
        assertThat(rows.get(1).managerCode()).isEqualTo("E1");
        assertThat(rows.get(1).rowNo()).isEqualTo(2);
    }

    @Test
    void theIsHeadColumnIsOptionalAndReadAsTyped() {
        List<ImportRowInput> rows = ImportFileParser.parse(
                "employee_code,full_name,department_code,position_name,is_head\nE1,Asha,TECH,TL, Yes \nE2,Ravi,TECH,SE,\n");
        assertThat(rows.get(0).isHead()).isEqualTo("Yes");
        assertThat(rows.get(1).isHead()).isNull();
        assertThat(ImportFileParser.parse("employee_code,full_name,department_code,position_name\nE1,Asha,TECH,TL").get(0).isHead())
                .isNull();
    }

    @Test
    void blankCellsBecomeNullAndShortRowsAreTolerated() {
        List<ImportRowInput> rows = ImportFileParser.parse(
                "employee_code,full_name,department_code,position_name,email\nE1,Asha,TECH,TL");
        assertThat(rows.get(0).email()).isNull();
    }

    @Test
    void rejectsAFileWithoutTheRequiredColumns() {
        assertThatThrownBy(() -> ImportFileParser.parse("employee_code,full_name\nE1,Asha"))
                .isInstanceOf(OrgStructureException.class)
                .hasMessageContaining("department_code").hasMessageContaining("position_name");
        assertThatThrownBy(() -> ImportFileParser.parse("")).isInstanceOf(OrgStructureException.class);
    }
}
