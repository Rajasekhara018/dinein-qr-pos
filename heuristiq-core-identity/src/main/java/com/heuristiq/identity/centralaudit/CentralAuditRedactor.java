package com.heuristiq.identity.centralaudit;

import static com.heuristiq.identity.centralaudit.CentralAuditConstants.MASK;
import static com.heuristiq.identity.centralaudit.CentralAuditConstants.MAX_DETAIL_VALUE;
import static com.heuristiq.identity.centralaudit.CentralAuditConstants.PHONE;
import static com.heuristiq.identity.centralaudit.CentralAuditConstants.EMAIL;
import static com.heuristiq.identity.centralaudit.CentralAuditConstants.SECRET_KEY;

/**
 * Pure redaction applied to audit values before they are stored in the outbox: values under secret-looking keys are
 * replaced with {@code ***}, e-mail addresses and phone numbers inside text are masked, and the size is capped.
 * Same rules as the audit publisher of the admin service (copied, not shared, so the services stay independent).
 */
public final class CentralAuditRedactor {

    private CentralAuditRedactor() {
    }

    /** @return true when the key names a credential or secret */
    public static boolean isSecretKey(String key) {
        return key != null && SECRET_KEY.matcher(key).matches();
    }

    /** Value to store for {@code key}: the mask for a secret key, otherwise the text with contacts masked and capped. */
    public static String redactValue(String key, String value) {
        if (value == null) {
            return null;
        }
        return isSecretKey(key) ? MASK : text(value);
    }

    /** Masks e-mail addresses and phone numbers in free text and caps its length. */
    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String masked = PHONE.matcher(EMAIL.matcher(value).replaceAll(MASK)).replaceAll(MASK);
        return masked.length() > MAX_DETAIL_VALUE ? masked.substring(0, MAX_DETAIL_VALUE) : masked;
    }
}
