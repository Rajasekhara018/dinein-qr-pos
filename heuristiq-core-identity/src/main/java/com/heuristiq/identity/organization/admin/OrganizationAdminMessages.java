package com.heuristiq.identity.organization.admin;

/** The wording of what an organization's first administrator is told. */
final class OrganizationAdminMessages {

    private OrganizationAdminMessages() {
    }

    /**
     * One ready sentence about signing in. A new account gets its email and one-time password (changed at first sign-in);
     * a person who already had an account is told to use their existing one, and never sees a password.
     */
    static String signInDetails(String email, String temporaryPassword, String loginUrl) {
        String where = loginUrl == null || loginUrl.isBlank() ? "" : " at " + loginUrl;
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            return "Sign in" + where + " with " + email + " and your existing password.";
        }
        return "Sign in" + where + " with " + email + " and the one-time password " + temporaryPassword
                + ". You will be asked to change it the first time you sign in.";
    }
}
