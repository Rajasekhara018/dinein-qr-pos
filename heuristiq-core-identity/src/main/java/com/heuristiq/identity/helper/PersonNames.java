package com.heuristiq.identity.helper;

/**
 * Turns the one text an administrator types as a person's name ("QA Retest UI Owner") into the parts UAM stores. Pure and stateless.
 * The text is split at its last space: everything before it is the first name, the last word is the last name, so "Anil Kumar Reddy"
 * is first name "Anil Kumar" and last name "Reddy". A name of one word stays a first name with the last name blank. Nothing here
 * decides who may be named or what a name may contain; that belongs to the callers' validation.
 */
public final class PersonNames {

    private PersonNames() {
    }

    /**
     * The stored parts of a typed name; runs of spaces count as one and the ends are trimmed.
     *
     * @return the name, or {@code null} for a null or blank text
     */
    public static RName split(String fullName) {
        if (fullName == null) {
            return null;
        }
        String name = fullName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return null;
        }
        int cut = name.lastIndexOf(' ');
        if (cut < 0) {
            return new RName(name);
        }
        return new RName(name.substring(0, cut), "", name.substring(cut + 1));
    }

    /**
     * The name as one line for lists and messages: the parts joined by single spaces.
     *
     * @return the line, or {@code null} when there is no name or it is blank
     */
    public static String display(RName name) {
        if (name == null) {
            return null;
        }
        String line = name.getFullName().trim().replaceAll("\\s+", " ");
        return line.isEmpty() ? null : line;
    }
}
