package com.heuristiq.uam.orgstructure.domain;

import java.util.regex.Pattern;

/** Fixed values of the bulk department import (docs/ORG_DEPARTMENT_IMPORT_DESIGN.md). */
public final class DepartmentImportConstants {

    /** Most levels a department may sit below the top (a top-level department is level 1). */
    public static final int MAX_UNIT_DEPTH = 12;

    /** Longest {@code unit_type} cell accepted (the column holds 50 characters). */
    public static final int MAX_UNIT_TYPE_LENGTH = 50;

    /** A department code: letters, digits, dot, underscore and hyphen only (it ends up in paths and search keys). */
    public static final Pattern CODE = Pattern.compile("^[A-Za-z0-9._-]+$");

    /**
     * Upper bound on validation rounds. A round either settles the file or rejects at least one more row (a parent rejected in a
     * round makes its children rejected in the next), and a chain is at most {@link #MAX_UNIT_DEPTH} levels, so a few rounds always
     * suffice; the bound only guards against a bug turning into an endless loop.
     */
    public static final int MAX_VALIDATION_ROUNDS = 40;

    /**
     * Upper bound on apply passes. Each pass creates the departments whose parent now exists, so the number of passes needed is the
     * depth of the deepest chain in the file; the extra two are slack for the chain that starts below an existing department.
     */
    public static final int MAX_APPLY_PASSES = MAX_UNIT_DEPTH + 2;

    /** Separator inside an error detail that carries two values, such as {@code PARENT_REJECTED:PARENT:7}. */
    public static final String ERROR_DETAIL_SEPARATOR = ":";

    /** Entity type of the audit log entry written when a department import ends. */
    public static final String AUDIT_ENTITY_TYPE = "ORG_DEPARTMENT_IMPORT";

    private DepartmentImportConstants() {
    }
}
