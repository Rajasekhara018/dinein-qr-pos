package com.heuristq.dinein.auth.dto;

import com.heuristq.dinein.staff.domain.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Size(max = 50) String username,
                               @NotBlank @Size(max = 72) String password) {
    }

    public record ChangePasswordRequest(@NotBlank @Size(max = 72) String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    /** Either {@code password} or {@code pin} must be provided. */
    public record KitchenDeviceRequest(@NotBlank @Size(max = 50) String username,
                                       @Size(max = 72) String password,
                                       @Size(max = 6) String pin,
                                       @Size(max = 60) String deviceName) {
    }

    public record StaffInfo(Long id, String username, String displayName, StaffRole role,
                            boolean mustChangePassword) {
    }

    public record TokenResponse(String accessToken, long expiresIn, StaffInfo user) {
    }

    public record DeviceTokenResponse(String deviceToken, Instant expiresAt, StaffInfo user) {
    }

    public record MeResponse(StaffInfo user, boolean device) {
    }
}
