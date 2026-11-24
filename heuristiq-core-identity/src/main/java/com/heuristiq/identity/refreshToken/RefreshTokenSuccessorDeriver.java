package com.heuristiq.uam.refreshToken;

import com.heuristiq.uam.config.UamProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Derives a rotated refresh token from the token it replaces: {@code HMAC(key, presentedRaw | successorTokenId)}.
 *
 * <p>Only hashes are stored, so this is what lets a replay inside the grace window be answered with the family's
 * <em>current</em> successor rather than a fresh branch: whoever still holds the previous raw token can have the
 * server recompute the successor, and nobody else can (the key never leaves UAM, and the successor id is random and
 * stored only server side). The key is derived from the token-signing secret with a fixed label, so no new secret is
 * needed and the two uses cannot be confused.
 */
@Component
public class RefreshTokenSuccessorDeriver {

    private final SecretKeySpec key;

    public RefreshTokenSuccessorDeriver(UamProperties properties) {
        String secret = properties.getAuth().getJwtSecret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("railcab.uam.auth.jwt-secret must be set");
        }
        byte[] derived = mac(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), RefreshTokenConstants.HMAC_ALGORITHM),
                RefreshTokenConstants.SUCCESSOR_KEY_LABEL);
        this.key = new SecretKeySpec(derived, RefreshTokenConstants.HMAC_ALGORITHM);
    }

    /** The raw successor token for {@code presentedRaw} and the successor's random token id (URL-safe, no padding). */
    public String deriveSuccessor(String presentedRaw, String successorTokenId) {
        byte[] out = mac(key, presentedRaw + RefreshTokenConstants.DERIVATION_SEPARATOR + successorTokenId);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(out);
    }

    private static byte[] mac(SecretKeySpec keySpec, String input) {
        try {
            Mac mac = Mac.getInstance(RefreshTokenConstants.HMAC_ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 not available", e);
        }
    }
}
