package com.heuristiq.identity.users.repository;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.users.UserPhoneConstants;
import org.springframework.dao.DataIntegrityViolationException;

/** Turns the database's unique-phone violation into the application's conflict error (never a raw 500). */
public final class PhoneDuplicates {

    private PhoneDuplicates() {
    }

    /**
     * @return a 409 {@link UamException} when {@code e} is the canonical-phone unique violation of either backend,
     *         otherwise {@code e} itself (other violations, such as username or email, keep their existing handling)
     */
    public static RuntimeException translate(DataIntegrityViolationException e) {
        String text = e.getMostSpecificCause().getMessage();
        if (text != null && (text.contains(UserPhoneConstants.PG_UNIQUE_PHONE_E164)
                || text.contains(UserPhoneConstants.MONGO_UNIQUE_PHONE_E164))) {
            return UamException.conflict(UserPhoneConstants.PHONE_TAKEN_MESSAGE);
        }
        return e;
    }
}
