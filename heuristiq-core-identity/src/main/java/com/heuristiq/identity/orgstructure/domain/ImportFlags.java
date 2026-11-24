package com.heuristiq.uam.orgstructure.domain;

import java.util.Locale;
import java.util.Set;

/** Reads the yes/no cells of the import files (yes, true, 1 / no, false, 0, in any case); a blank cell is "no". */
public final class ImportFlags {
    private static final Set<String> YES = Set.of("yes", "true", "1");
    private static final Set<String> NO = Set.of("no", "false", "0");

    private ImportFlags() {
    }

    /** True when the cell is an accepted yes or no (or blank). */
    public static boolean isValid(String cell) {
        if (cell == null) {
            return true;
        }
        String key = key(cell);
        return YES.contains(key) || NO.contains(key);
    }

    /** True for yes, false for no or blank; only meaningful when {@link #isValid} said so. */
    public static boolean isYes(String cell) {
        return cell != null && YES.contains(key(cell));
    }

    private static String key(String cell) {
        return cell.trim().toLowerCase(Locale.ROOT);
    }
}
