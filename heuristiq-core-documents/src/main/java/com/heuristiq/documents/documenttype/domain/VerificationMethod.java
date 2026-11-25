package com.heuristiq.documents.documenttype.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How a document of a type is to be verified by a third party (or by staff). Configuration only for now: DMS records
 * which method a type asks for, it does not call any provider and keeps no per-document verification status.
 *
 * <p>{@link #NONE} is the value of a type that needs no verification, and of every type stored before the field existed.
 */
public enum VerificationMethod {
    NONE,
    MANUAL_REVIEW,
    AADHAAR_DIGILOCKER,
    PAN_VERIFICATION,
    DRIVING_LICENCE_SARATHI,
    VEHICLE_RC_VAHAN,
    BANK_PENNY_DROP;

    /**
     * Parses a method key.
     *
     * @param key the key, case-insensitive; surrounding blanks are ignored
     * @return the method, or empty when the key is null, blank or unknown
     */
    public static Optional<VerificationMethod> fromKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(m -> m.name().equals(normalized)).findFirst();
    }

    /** The stored key read back: a missing or unknown value (old rows, old documents) is {@link #NONE}. */
    public static VerificationMethod fromStored(String key) {
        return fromKey(key).orElse(NONE);
    }
}
