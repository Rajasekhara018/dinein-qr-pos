package com.heuristiq.notification.domain.model;

/**
 * How an automated voice call ended, as published on {@code nms-voice-responses} (names fixed by the ride flow contract).
 */
public enum VoiceCallOutcome {
    /** The customer picked up (a pressed digit, if any, travels with it). */
    ANSWERED,
    /** Nobody picked up, the line was busy, or the call was cancelled before it was answered. */
    NO_ANSWER,
    /** The call could not be placed or broke off. */
    FAILED;

    /** The delivery status recorded on the call's attempt: answered = delivered, anything else = failed. */
    public DeliveryStatus deliveryStatus() {
        return this == ANSWERED ? DeliveryStatus.DELIVERED : DeliveryStatus.FAILED;
    }
}
