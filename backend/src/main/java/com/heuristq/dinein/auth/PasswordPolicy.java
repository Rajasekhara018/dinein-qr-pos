package com.heuristq.dinein.auth;

import com.heuristq.dinein.shared.exception.ApiException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** At least 8 characters (BCrypt caps at 72 bytes) with at least one letter and one digit. */
    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw ApiException.badRequest("WEAK_PASSWORD",
                    "Password must be 8-72 characters and contain at least one letter and one digit");
        }
    }
}
