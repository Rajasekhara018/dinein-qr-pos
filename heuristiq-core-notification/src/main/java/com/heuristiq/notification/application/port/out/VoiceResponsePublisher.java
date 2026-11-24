package com.railcab.nms.application.port.out;

import com.railcab.nms.application.dto.VoiceResponseEvent;

/** Publishes the answer of an automated voice call to the services waiting for it (Kafka {@code nms-voice-responses}). */
public interface VoiceResponsePublisher {

    /**
     * @return true once the broker has accepted the record; false when it could not be published (the provider callback is
     *         then answered with an error so the provider retries)
     */
    boolean publish(VoiceResponseEvent event);
}
