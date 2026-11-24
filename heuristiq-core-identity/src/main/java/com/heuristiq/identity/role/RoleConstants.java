package com.heuristiq.uam.role;

/** Fixed values of the role rules. */
public final class RoleConstants {

    /** A role code (after trimming and upper-casing): starts with a letter, then letters, digits or underscores; 3 to 50 characters. */
    public static final String CODE_REGEX = "^[A-Z][A-Z0-9_]{2,49}$";

    private RoleConstants() {
    }
}
