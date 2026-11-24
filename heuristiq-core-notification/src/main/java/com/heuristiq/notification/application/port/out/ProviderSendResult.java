package com.railcab.nms.application.port.out;

/**
 * What one provider call answered, beyond the plain success flag of {@link ChannelProvider#send}:
 * which concrete provider handled it, the provider's own message id (the key a delivery receipt is
 * matched on later), and, on failure, a short code and a sanitised reason.
 *
 * @param success           the provider accepted the message
 * @param providerName      concrete provider (digimile, twilio, meta, fcm, smtp...); may be null when unknown
 * @param providerMessageId id the provider gave the message; null when it gives none
 * @param providerStatus    raw status text from the provider response; may be null
 * @param errorCode         short machine code on failure (for example PROVIDER_REJECTED)
 * @param errorMessage      human-readable reason on failure; must not contain secrets
 */
public record ProviderSendResult(boolean success, String providerName, String providerMessageId,
        String providerStatus, String errorCode, String errorMessage) {

    /** Error code used when a provider only said "no" (a plain {@code false} from {@code send}). */
    public static final String CODE_REJECTED = "PROVIDER_REJECTED";

    /** Error code used when the provider call threw. */
    public static final String CODE_EXCEPTION = "PROVIDER_EXCEPTION";

    /** Reason used when the provider gave none; kept identical to the pre-attempt-model wording. */
    public static final String DEFAULT_FAILURE_MESSAGE = "Provider returned false";

    public static ProviderSendResult ok(String providerName, String providerMessageId, String providerStatus) {
        return new ProviderSendResult(true, providerName, providerMessageId, providerStatus, null, null);
    }

    public static ProviderSendResult failed(String providerName, String errorCode, String errorMessage) {
        return new ProviderSendResult(false, providerName, null, null,
                errorCode == null ? CODE_REJECTED : errorCode,
                errorMessage == null ? DEFAULT_FAILURE_MESSAGE : errorMessage);
    }
}
