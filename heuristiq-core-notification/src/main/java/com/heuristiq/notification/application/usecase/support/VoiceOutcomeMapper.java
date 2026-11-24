package com.railcab.nms.application.usecase.support;

import java.util.Locale;
import java.util.Optional;

import com.railcab.nms.domain.model.VoiceCallOutcome;

/** Reads a voice provider's status words and pressed digits into the NMS vocabulary. Pure, stateless. */
public final class VoiceOutcomeMapper {

    private VoiceOutcomeMapper() {
    }

    /**
     * The call outcome: a pressed digit always means ANSWERED; otherwise the status decides. Empty when neither says
     * anything NMS understands.
     */
    public static Optional<VoiceCallOutcome> outcome(String rawStatus, String digit) {
        if (digit != null) {
            return Optional.of(VoiceCallOutcome.ANSWERED);
        }
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        String status = rawStatus.trim().toLowerCase(Locale.ROOT);
        if (VoiceCallbackConstants.ANSWERED_STATUSES.contains(status)) {
            return Optional.of(VoiceCallOutcome.ANSWERED);
        }
        if (VoiceCallbackConstants.NO_ANSWER_STATUSES.contains(status)) {
            return Optional.of(VoiceCallOutcome.NO_ANSWER);
        }
        if (VoiceCallbackConstants.FAILED_STATUSES.contains(status)) {
            return Optional.of(VoiceCallOutcome.FAILED);
        }
        return Optional.empty();
    }

    /** The pressed digits without the quotes and spaces some providers add ({@code "1"}); null when none or not keypad digits. */
    public static String digit(String rawDigits) {
        if (rawDigits == null) {
            return null;
        }
        String cleaned = rawDigits.replace("\"", "").replace("'", "").trim();
        return VoiceCallbackConstants.DIGITS.matcher(cleaned).matches() ? cleaned : null;
    }

    /** {@code <callSid>_<STATUS>} or {@code <callSid>_DIGITS-<digits>}: the same report always gives the same id. */
    public static String eventId(String callSid, String rawStatus, String digit) {
        String part = digit != null
                ? VoiceCallbackConstants.EVENT_ID_DIGITS_PREFIX + digit
                : rawStatus.trim().toUpperCase(Locale.ROOT);
        return callSid.trim() + VoiceCallbackConstants.EVENT_ID_SEPARATOR + part;
    }
}
