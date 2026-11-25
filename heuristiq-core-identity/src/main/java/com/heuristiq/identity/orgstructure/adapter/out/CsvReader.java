package com.heuristiq.identity.orgstructure.adapter.out;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 CSV reader: quoted cells, doubled quotes, commas and line breaks inside quotes, CRLF or LF. */
final class CsvReader {
    private static final char BOM = '﻿';
    private static final char QUOTE = '"';

    private CsvReader() {
    }

    /** Splits the text into rows of cells; completely empty lines are skipped. */
    static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int start = !text.isEmpty() && text.charAt(0) == BOM ? 1 : 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == QUOTE) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == QUOTE) {
                        cell.append(QUOTE);
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == QUOTE) {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, cell);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        endRow(rows, row, cell);
        return rows;
    }

    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder cell) {
        row.add(cell.toString());
        cell.setLength(0);
        boolean blank = row.size() == 1 && row.get(0).isBlank();
        if (!blank) {
            rows.add(row);
        }
    }
}
