package com.heuristiq.uam.orgstructure.domain;

import java.util.regex.Pattern;

/** Fixed values of the bulk ladders and positions import (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md, "Everything by CSV"). */
public final class LadderImportConstants {

    /** Lowest and highest rank a position may have; a higher rank is more senior. */
    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 99;

    /** A rank cell: one or two digits (the range is checked after reading it). */
    public static final Pattern RANK = Pattern.compile("^\\d{1,2}$");

    /** Separator inside an error detail, such as {@code DUPLICATE_ROW:3}. */
    public static final String ERROR_DETAIL_SEPARATOR = ":";

    /** Entity type of the audit log entry written when a ladders import ends. */
    public static final String AUDIT_ENTITY_TYPE = "ORG_LADDER_IMPORT";

    private LadderImportConstants() {
    }
}
