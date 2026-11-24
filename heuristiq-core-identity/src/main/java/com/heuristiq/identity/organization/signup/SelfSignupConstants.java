package com.heuristiq.uam.organization.signup;

import java.util.regex.Pattern;

/** Fixed values of the public self-signup organization choice. */
final class SelfSignupConstants {

    /** Most organizations the public list ever returns (an applicant picks from a short list; more is a misconfiguration). */
    static final int MAX_PUBLIC_ORGANIZATIONS = 100;

    /** Shape of an application or organization code; anything else is treated as unknown, never as an error. */
    static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_\\-]{1,50}$");

    /** Resolution refused: unknown or not offered. One message for both so the answer does not reveal which organizations exist. */
    static final String MSG_NOT_AVAILABLE = "That organization is not available for self-signup to this application";

    private SelfSignupConstants() {
    }
}
