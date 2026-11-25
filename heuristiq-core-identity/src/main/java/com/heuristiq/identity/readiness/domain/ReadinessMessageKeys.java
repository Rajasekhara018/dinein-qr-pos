package com.heuristiq.identity.readiness.domain;

import java.util.Locale;

/**
 * Builds message keys in the contract's form {@code readiness.<service>.<camelCaseCheck>.<status lower case>}, e.g.
 * {@code WALLET_APP_REGISTERED} + FAIL gives {@code readiness.wallet.appRegistered.fail}. Used for UAM's own checks and for
 * the steps Rider_UAM fills in itself (a service down, a check not reported, connectivity, go-live review).
 */
public final class ReadinessMessageKeys {

    private static final char WORD_SEPARATOR = '_';

    private ReadinessMessageKeys() {
    }

    /** The message key of a check id reported by {@code source} with {@code status}. */
    public static String of(ReadinessSource source, String checkId, ReadinessStatus status) {
        return ReadinessConstants.MESSAGE_KEY_ROOT + ReadinessConstants.MESSAGE_KEY_SEPARATOR + source.slug()
                + ReadinessConstants.MESSAGE_KEY_SEPARATOR + checkPart(source, checkId)
                + ReadinessConstants.MESSAGE_KEY_SEPARATOR + status.keySuffix();
    }

    /** The check part of the key: the id without its service prefix, in lower camel case. */
    static String checkPart(ReadinessSource source, String checkId) {
        String base = checkId.startsWith(source.checkPrefix()) ? checkId.substring(source.checkPrefix().length()) : checkId;
        StringBuilder camel = new StringBuilder(base.length());
        boolean upperNext = false;
        for (char c : base.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == WORD_SEPARATOR) {
                upperNext = camel.length() > 0;
                continue;
            }
            camel.append(upperNext ? Character.toUpperCase(c) : c);
            upperNext = false;
        }
        return camel.toString();
    }
}
