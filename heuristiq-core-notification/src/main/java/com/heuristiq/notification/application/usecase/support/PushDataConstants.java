package com.heuristiq.notification.application.usecase.support;

import java.util.List;

/**
 * Keys and limits of the structured data map sent with every push (contract: docs/RIDE_FLOW_FIXES_2026-11.md section 2).
 * The apps route a push by these keys, so they never change once released.
 */
public final class PushDataConstants {

    /** Upper-case event name ({@code request.event}). */
    public static final String KEY_TYPE = "type";
    public static final String KEY_EVENT_CODE = "eventCode";
    public static final String KEY_ENTITY_TYPE = "entityType";
    public static final String KEY_ENTITY_ID = "entityId";
    public static final String KEY_RIDE_ID = "rideId";
    /** Destination Rider_Web's service worker opens on click. */
    public static final String KEY_URL = "url";
    public static final String URL_NOTIFICATIONS = "/notifications";
    /** Added only for the VOICE channel, so a provider callback can be matched to the message. Never in a push. */
    public static final String KEY_MESSAGE_ID = "messageId";

    /** Entity type rides send; a RIDE push uses its entity id as {@code rideId}. */
    public static final String ENTITY_TYPE_RIDE = "RIDE";

    /**
     * A variable whose name contains one of these (case-insensitive) is never copied into the data map: credentials, links
     * that carry a token (reset/sign-in links), and personal contact or account data, which a push payload has no need for.
     */
    public static final List<String> SENSITIVE_KEY_PARTS = List.of("otp", "password", "secret", "token", "pin", "cvv",
            "link", "signin", "credential", "email", "phone", "mobile", "card", "account");

    /** Longest value copied for one variable, in characters. */
    public static final int MAX_VALUE_LENGTH = 512;

    /** Largest whole map, in UTF-8 bytes of keys plus values (FCM allows 4 KB in total). */
    public static final int MAX_TOTAL_BYTES = 3500;

    private PushDataConstants() {
    }
}
