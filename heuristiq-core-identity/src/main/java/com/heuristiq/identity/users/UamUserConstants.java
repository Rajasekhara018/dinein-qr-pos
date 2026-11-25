package com.heuristiq.identity.users;

/** Fixed values of the user management feature. */
public final class UamUserConstants {

    /** Most user ids one batch lookup may carry. */
    public static final int MAX_BATCH_LOOKUP = 100;

    /** Base path of the people directory endpoint. */
    public static final String DIRECTORY_PATH = "/api/v1/uam/directory/users";

    /** Most people the directory returns in one call. */
    public static final int DIRECTORY_MAX_RESULTS = 100;

    /** People the directory returns when no limit is given. */
    public static final int DIRECTORY_DEFAULT_RESULTS = 50;

    /** Longest email address (RFC 5321). */
    public static final int EMAIL_MAX_LENGTH = 254;

    /**
     * An email address, the same rule the forms check (Rider_Web {@code USER_EMAIL_PATTERN}): something, one '@', something, a
     * dot and a last part of at least two characters, none of them a space or another '@'. Rejects "bad@", "a@b" and spaces.
     */
    public static final String EMAIL_REGEX = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    /** Password the platform requires when no security policy asks for more (also the lowest a policy can set). */
    public static final int DEFAULT_MIN_PASSWORD_LENGTH = 8;

    /** Longest password a user may set. */
    public static final int PASSWORD_MAX_LENGTH = 128;

    private UamUserConstants() {
    }
}
