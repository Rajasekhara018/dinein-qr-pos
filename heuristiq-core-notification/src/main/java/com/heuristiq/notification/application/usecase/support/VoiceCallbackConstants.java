package com.heuristiq.notification.application.usecase.support;

import java.util.Set;
import java.util.regex.Pattern;

/** Vocabulary used to read a voice provider's call report (lower-case status words, digit format, event id parts). */
public final class VoiceCallbackConstants {

    /** Statuses meaning the customer picked up. */
    public static final Set<String> ANSWERED_STATUSES = Set.of("completed", "answered", "in-progress", "in_progress");
    /** Statuses meaning nobody picked up. */
    public static final Set<String> NO_ANSWER_STATUSES = Set.of("no-answer", "no_answer", "noanswer", "busy",
            "canceled", "cancelled");
    /** Statuses meaning the call failed. */
    public static final Set<String> FAILED_STATUSES = Set.of("failed", "error");

    /** A usable digit answer: keypad characters only, short. */
    public static final Pattern DIGITS = Pattern.compile("[0-9*#]{1,8}");

    /** Separator between the call id and the status in an event id. */
    public static final String EVENT_ID_SEPARATOR = "_";
    /** Status part of an event id for a digit-only report: {@code DIGITS-<digits>}. */
    public static final String EVENT_ID_DIGITS_PREFIX = "DIGITS-";

    private VoiceCallbackConstants() {
    }
}
