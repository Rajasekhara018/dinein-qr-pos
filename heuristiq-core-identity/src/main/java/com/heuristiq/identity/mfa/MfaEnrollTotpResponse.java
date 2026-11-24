package com.heuristiq.uam.mfa;

/**
 * {@code secret} and {@code qrCodeImageBase64} are only ever returned here, at enrollment time —
 * {@link UserMfa} hides both behind {@code @JsonIgnore} everywhere else.
 */
public record MfaEnrollTotpResponse(
        String id,
        String secret,
        String qrCodeImageBase64,
        String otpAuthUrl
) {
}
