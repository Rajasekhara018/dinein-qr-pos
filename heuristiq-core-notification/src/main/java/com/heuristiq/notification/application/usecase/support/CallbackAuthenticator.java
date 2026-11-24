package com.railcab.nms.application.usecase.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.railcab.nms.application.port.in.CallbackAuthenticationUseCase;
import com.railcab.nms.infrastructure.config.NmsProperties;

import lombok.RequiredArgsConstructor;

/**
 * Checks the proof each provider attaches to a callback. All comparisons are constant-time, and a
 * check fails closed: while a provider's secret is not configured nothing is accepted from it.
 */
@Component
@RequiredArgsConstructor
public class CallbackAuthenticator implements CallbackAuthenticationUseCase {

    private final NmsProperties props;

    /** Shared secret (Digimile and generic endpoints), sent in {@code X-NMS-Callback-Secret}. */
    @Override
    public boolean sharedSecretValid(String presented) {
        return equalsConstantTime(props.getCallbacks().getSharedSecret(), presented);
    }

    /**
     * Twilio: {@code X-Twilio-Signature} is base64(HMAC-SHA1(authToken, url + each parameter name and
     * value, names sorted)). The URL must be exactly the one Twilio was given, so it comes from
     * {@code nms.callbacks.twilio-callback-url}, not from the (proxied) request.
     */
    @Override
    public boolean twilioSignatureValid(Map<String, List<String>> params, String presentedSignature) {
        String url = props.getCallbacks().getTwilioCallbackUrl();
        String authToken = props.getTwilio().getAuthToken();
        if (!StringUtils.hasText(url) || !StringUtils.hasText(authToken) || !StringUtils.hasText(presentedSignature)) {
            return false;
        }
        StringBuilder data = new StringBuilder(url);
        new TreeMap<>(params).forEach((name, values) -> values.stream().sorted()
                .forEach(value -> data.append(name).append(value)));
        byte[] mac = hmac(CallbackSecurityConstants.HMAC_SHA1, authToken, data.toString().getBytes(StandardCharsets.UTF_8));
        return mac != null && equalsConstantTime(Base64.getEncoder().encodeToString(mac), presentedSignature);
    }

    /** Meta: {@code X-Hub-Signature-256} is {@code sha256=} + hex(HMAC-SHA256(appSecret, raw body)). */
    @Override
    public boolean metaSignatureValid(byte[] rawBody, String presentedHeader) {
        String secret = props.getCallbacks().getMetaAppSecret();
        if (!StringUtils.hasText(secret) || rawBody == null || !StringUtils.hasText(presentedHeader)
                || !presentedHeader.startsWith(CallbackSecurityConstants.META_SIGNATURE_PREFIX)) {
            return false;
        }
        byte[] mac = hmac(CallbackSecurityConstants.HMAC_SHA256, secret, rawBody);
        String expected = mac == null ? null : HexFormat.of().formatHex(mac);
        return equalsConstantTime(expected,
                presentedHeader.substring(CallbackSecurityConstants.META_SIGNATURE_PREFIX.length()).toLowerCase());
    }

    /**
     * HTTP basic credentials whose password is the voice-only secret (the user name is ignored). For providers that cannot
     * send a custom header, such as Exotel, whose callback URL carries the credentials as user info.
     */
    @Override
    public boolean basicVoiceSecretValid(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || authorizationHeader.length() <= CallbackSecurityConstants.BASIC_AUTH_PREFIX.length()
                || !authorizationHeader.regionMatches(true, 0, CallbackSecurityConstants.BASIC_AUTH_PREFIX, 0,
                        CallbackSecurityConstants.BASIC_AUTH_PREFIX.length())) {
            return false;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(
                    authorizationHeader.substring(CallbackSecurityConstants.BASIC_AUTH_PREFIX.length()).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
        int separator = decoded.indexOf(CallbackSecurityConstants.BASIC_AUTH_SEPARATOR);
        return separator >= 0 && voiceSecretValid(decoded.substring(separator + 1));
    }

    /**
     * Voice-only secret: Exotel carries it in the callback URL it stores, so it is kept apart from the shared secret of the
     * other receipt endpoints and can be rotated on its own if a provider dashboard or log ever exposes it.
     */
    @Override
    public boolean voiceSecretValid(String presented) {
        return equalsConstantTime(props.getCallbacks().getVoiceSharedSecret(), presented);
    }

    /** Meta's subscription handshake token. */
    @Override
    public boolean metaVerifyTokenValid(String presented) {
        return equalsConstantTime(props.getCallbacks().getMetaVerifyToken(), presented);
    }

    /** Mailgun: signature is hex(HMAC-SHA256(signingKey, timestamp + token)). */
    @Override
    public boolean mailgunSignatureValid(String timestamp, String token, String presentedSignature) {
        String key = props.getCallbacks().getMailgunSigningKey();
        if (!StringUtils.hasText(key) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(token)
                || !StringUtils.hasText(presentedSignature)) {
            return false;
        }
        byte[] mac = hmac(CallbackSecurityConstants.HMAC_SHA256, key, (timestamp + token).getBytes(StandardCharsets.UTF_8));
        return mac != null && equalsConstantTime(HexFormat.of().formatHex(mac), presentedSignature.toLowerCase());
    }

    private static byte[] hmac(String algorithm, String key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    private static boolean equalsConstantTime(String expected, String presented) {
        if (!StringUtils.hasText(expected) || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
