package com.heuristiq.identity.users;

import com.heuristiq.identity.common.UamException;

import java.util.regex.Pattern;

/**
 * Checks of the values an administrator types for a user account: the email address and the password. Pure and stateless;
 * every failure is a 400 with a message the form can show as given.
 */
public final class UserInputValidator {

    private static final Pattern EMAIL = Pattern.compile(UamUserConstants.EMAIL_REGEX);

    private UserInputValidator() {
    }

    /**
     * An email address, when one is given (blank means none): at most 254 characters and matching
     * {@link UamUserConstants#EMAIL_REGEX}.
     *
     * @throws UamException when the address is malformed
     */
    public static void requireValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String value = email.trim();
        if (value.length() > UamUserConstants.EMAIL_MAX_LENGTH || !EMAIL.matcher(value).matches()) {
            throw new UamException("Enter a valid email address");
        }
    }

    /**
     * A password an administrator sets: at least {@code minimumLength} characters (the effective policy minimum), at most
     * 128, with at least one letter and one digit.
     *
     * @throws UamException when it does not meet the rules
     */
    public static void requireStrongPassword(String password, int minimumLength) {
        if (password == null || password.length() < minimumLength) {
            throw new UamException("Password must be at least " + minimumLength + " characters");
        }
        if (password.length() > UamUserConstants.PASSWORD_MAX_LENGTH) {
            throw new UamException("Password must be at most " + UamUserConstants.PASSWORD_MAX_LENGTH + " characters");
        }
        // ASCII letters and digits, as the forms check (Rider_Web USER_PASSWORD_PATTERN).
        boolean letter = password.chars().anyMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'));
        boolean digit = password.chars().anyMatch(c -> c >= '0' && c <= '9');
        if (!letter || !digit) {
            throw new UamException("Password must contain at least one letter and one digit");
        }
    }
}
