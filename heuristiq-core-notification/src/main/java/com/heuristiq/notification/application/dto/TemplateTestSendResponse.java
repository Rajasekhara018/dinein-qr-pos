package com.railcab.nms.application.dto;

/**
 * Outcome of a test send.
 *
 * @param sent      the provider accepted the message
 * @param provider  concrete provider that handled it; may be null
 * @param errorCode short code when it failed; null on success
 * @param message   reason when it failed; null on success
 */
public record TemplateTestSendResponse(boolean sent, String provider, String errorCode, String message) {
}
