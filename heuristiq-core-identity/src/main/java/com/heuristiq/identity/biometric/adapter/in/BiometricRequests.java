package com.heuristiq.uam.biometric.adapter.in;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request bodies of the biometric endpoints (each carried in {@code commonRequest.requestObject}). */
public final class BiometricRequests {

    private BiometricRequests() {
    }

    /** Enrol this installation's device key. */
    @Schema(description = "Enrols the app installation's device public key for fingerprint / Face ID sign-in.")
    public record Enrol(
            @Schema(description = "The app's random per-install UUID.", example = "6f1c2b9e-3c41-4b7a-9d55-0f6c1c3c8a11")
            @NotBlank @Size(max = 64) String installationId,
            @Schema(description = "Base64 (standard) of the X.509 SubjectPublicKeyInfo DER, or the same in PEM.")
            @NotBlank @Size(max = 4096) String publicKey,
            @Schema(description = "EC_P256_SHA256 or RSA_2048_SHA256.", example = "EC_P256_SHA256")
            @NotBlank String keyAlgorithm,
            @Schema(description = "ANDROID or IOS.", example = "ANDROID") @NotBlank String platform,
            @Schema(description = "The person's name for the phone.", example = "Pixel 8", nullable = true) @Size(max = 100) String deviceName,
            @Schema(description = "Optional: the current password, needed when the session was not signed in within the last few minutes.",
                    nullable = true) @Size(max = 256) String currentPassword) {
        @Override
        public String toString() {
            // Never print the password or the key.
            return "Enrol[installationId=" + installationId + ", keyAlgorithm=" + keyAlgorithm + ", platform=" + platform + "]";
        }
    }

    /** Ask for a sign-in challenge. */
    @Schema(description = "Asks for a one-time challenge to sign with the device key.")
    public record Challenge(
            @Schema(description = "The app's random per-install UUID.") @NotBlank @Size(max = 64) String installationId) {
    }

    /** Sign in with a device signature. */
    @Schema(description = "Signs in with the device key's signature over the challenge's UTF-8 bytes.")
    public record Verify(
            @Schema(description = "challengeId from the challenge answer.") @NotBlank @Size(max = 64) String challengeId,
            @Schema(description = "The app's random per-install UUID.") @NotBlank @Size(max = 64) String installationId,
            @Schema(description = "Base64 (standard) of the signature (DER for EC).") @NotBlank @Size(max = 1024) String signature) {
    }
}
