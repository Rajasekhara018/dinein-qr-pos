package com.heuristiq.identity.users;

/** Fixed names for the canonical (E.164) phone uniqueness of an account, shared by both persistence backends. */
public final class UserPhoneConstants {

    /** Postgres column and Mongo field holding the E.164 form of the account's phone. */
    public static final String COLUMN_PHONE_E164 = "phone_e164";
    public static final String FIELD_PHONE_E164 = "phoneE164";

    /** Unique index/constraint names (Postgres index, Mongo index). Used to recognise a phone duplicate in a database error. */
    public static final String PG_UNIQUE_PHONE_E164 = "uk_uam_user_phone_e164";
    public static final String MONGO_UNIQUE_PHONE_E164 = "uk_phone_e164";

    /** Legacy unique index on the national number alone, replaced by the canonical one. */
    public static final String MONGO_LEGACY_UNIQUE_PHONE_NUMBER = "uk_phone_number";

    /** Error text for a phone already held by another account (same wording as the application-level check). */
    public static final String PHONE_TAKEN_MESSAGE = "Phone number already exists";

    /** Users rewritten per batch by the Mongo phone migration. */
    public static final int PHONE_MIGRATION_BATCH_SIZE = 500;

    private UserPhoneConstants() {
    }
}
