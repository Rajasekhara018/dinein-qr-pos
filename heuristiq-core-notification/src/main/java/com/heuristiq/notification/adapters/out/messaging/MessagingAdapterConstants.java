package com.railcab.nms.adapters.out.messaging;

/** Fixed values of NMS's outbound Kafka publishers. */
public final class MessagingAdapterConstants {

    /** How long a voice response publish waits for the broker's acknowledgement, in seconds. */
    public static final long VOICE_RESPONSE_SEND_TIMEOUT_SECONDS = 5;

    private MessagingAdapterConstants() {
    }
}
