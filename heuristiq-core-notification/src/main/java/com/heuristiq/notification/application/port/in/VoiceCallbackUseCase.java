package com.railcab.nms.application.port.in;

import com.railcab.nms.application.dto.VoiceCallback;

/** Handles a voice provider's call report: records it on the attempt and publishes the call's answer. */
public interface VoiceCallbackUseCase {

    /** What became of one report. */
    enum Result {
        /** Recorded and published. */
        PUBLISHED,
        /** Recorded, but the call carries no entity, so there is nobody to tell. */
        NO_ENTITY,
        /** Recorded only: the call id matches no voice call NMS placed, so nothing is reported (a forged or stray report). */
        UNKNOWN_CALL,
        /** The report carried no call id or no status/digit NMS understands; nothing done. */
        IGNORED,
        /** The answer could not be published; the provider should retry. */
        PUBLISH_FAILED
    }

    Result handle(VoiceCallback callback);
}
