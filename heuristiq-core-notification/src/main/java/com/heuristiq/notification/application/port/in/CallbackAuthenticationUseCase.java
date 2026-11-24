package com.heuristiq.notification.application.port.in;

import java.util.List;
import java.util.Map;

/**
 * Verifies the proof a provider attaches to a delivery-receipt callback. Every check fails closed:
 * while the secret for a provider is not configured, nothing is accepted from it.
 */
public interface CallbackAuthenticationUseCase {

    /** Shared secret (Digimile and generic endpoints), as sent in {@code X-NMS-Callback-Secret}. */
    boolean sharedSecretValid(String presented);

    /** Twilio {@code X-Twilio-Signature} over the configured callback URL and the form parameters. */
    boolean twilioSignatureValid(Map<String, List<String>> params, String presentedSignature);

    /** Meta {@code X-Hub-Signature-256} header (with its {@code sha256=} prefix) over the raw body. */
    boolean metaSignatureValid(byte[] rawBody, String presentedHeader);

    /** Voice-only callback secret (Exotel), as sent in {@code X-NMS-Callback-Secret}; separate from the shared secret. */
    boolean voiceSecretValid(String presented);

    /** HTTP basic {@code Authorization} header whose password is the voice-only callback secret (Exotel voice callbacks). */
    boolean basicVoiceSecretValid(String authorizationHeader);

    /** Meta's subscription handshake token. */
    boolean metaVerifyTokenValid(String presented);

    /** Mailgun webhook signature block. */
    boolean mailgunSignatureValid(String timestamp, String token, String presentedSignature);
}
