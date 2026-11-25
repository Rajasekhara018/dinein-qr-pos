package com.heuristiq.identity.biometric.adapter.in;

import com.heuristiq.identity.biometric.application.BiometricUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** Answers of the biometric endpoints (carried in {@code CommonResponseObject.responseObject}). */
public final class BiometricResponses {

    private BiometricResponses() {
    }

    @Schema(description = "The enrolled device credential.")
    public record Enrolled(String credentialId, Instant createdAt,
                           @Schema(description = "When it expires unless used before (each sign-in moves it).") Instant expiresAt) {
        static Enrolled of(BiometricUseCase.EnrolResult result) {
            return new Enrolled(result.credentialId(), result.createdAt(), result.expiresAt());
        }
    }

    @Schema(description = "One of the caller's active enrolled devices.")
    public record Device(String credentialId, String deviceName, String platform, Instant createdAt, Instant lastUsedAt,
                         Instant expiresAt,
                         @Schema(description = "True when it is the installation named in the X-Installation-Id header.") boolean current) {
        static Device of(BiometricUseCase.DeviceView view) {
            return new Device(view.credentialId(), view.deviceName(), view.platform(), view.createdAt(), view.lastUsedAt(),
                    view.expiresAt(), view.current());
        }
    }

    @Schema(description = "A one-time sign-in challenge.")
    public record Challenge(String challengeId,
                            @Schema(description = "Base64 of 32 random bytes; sign the UTF-8 bytes of this text exactly.") String challenge,
                            Instant expiresAt) {
        static Challenge of(BiometricUseCase.ChallengeResult result) {
            return new Challenge(result.challengeId(), result.challenge(), result.expiresAt());
        }
    }
}
