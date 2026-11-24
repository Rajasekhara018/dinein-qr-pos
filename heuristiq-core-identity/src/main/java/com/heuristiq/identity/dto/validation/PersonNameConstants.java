package com.heuristiq.uam.dto.validation;

import java.util.regex.Pattern;

/** The person-name rule shared by self-registration (same rule as the onboarding forms and the apps). */
public final class PersonNameConstants {

    /**
     * Letters of any script (Indian scripts included) and their combining marks, spaces, '.', apostrophes (' and ’)
     * and hyphens; the first character must be a letter.
     */
    public static final String NAME_REGEX = "^[\\p{L}\\p{M}][\\p{L}\\p{M} .'’-]*$";

    /** {@link #NAME_REGEX} compiled once. */
    public static final Pattern NAME_PATTERN = Pattern.compile(NAME_REGEX);

    /** Shortest accepted name, after trimming. */
    public static final int MIN_LENGTH = 2;

    /** Longest accepted name, after trimming. */
    public static final int MAX_LENGTH = 80;

    /** Message returned when the name has characters outside the rule. */
    public static final String INVALID_CHARACTERS_MESSAGE =
            "Name may contain only letters, spaces, '.', apostrophe and hyphen.";

    /** Message returned when the trimmed name is too short or too long. */
    public static final String INVALID_LENGTH_MESSAGE = "Name must be between 2 and 80 characters.";

    private PersonNameConstants() {
    }
}
