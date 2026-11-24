package com.heuristiq.uam.helper;

/** Fixed values used when reading and validating phone numbers. */
final class PhoneConstants {

    /** Digits of a legacy Indian number stored without a country code. */
    static final int LEGACY_INDIAN_LENGTH = 10;

    /** Country prefix given to a legacy Indian number stored without a country code. */
    static final String LEGACY_INDIAN_PREFIX = "+91";

    /** Validation message for a number that cannot be parsed. */
    static final String INVALID_PHONE_MESSAGE = "must be a valid phone number";

    private PhoneConstants() {
    }
}
