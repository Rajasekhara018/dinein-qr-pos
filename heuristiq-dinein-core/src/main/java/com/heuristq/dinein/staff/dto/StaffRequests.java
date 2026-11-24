package com.heuristq.dinein.staff.dto;

import com.heuristq.dinein.staff.domain.StaffRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StaffRequests {

    private StaffRequests() {
    }

    public record CreateStaff(
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                    message = "letters, digits, dot, dash, underscore only") String username,
            @Size(max = 80) String displayName,
            @NotNull StaffRole role,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4-6 digits") String pin,
            @Email @Size(max = 120) String email,
            @Pattern(regexp = "^(\\d{10})?$", message = "Phone must be 10 digits") String phone) {
    }

    public record UpdateStaff(
            @Size(max = 80) String displayName,
            @NotNull StaffRole role,
            boolean active,
            @Size(min = 8, max = 72) String newPassword,
            @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4-6 digits") String pin,
            boolean clearPin,
            @Email @Size(max = 120) String email,
            @Pattern(regexp = "^(\\d{10})?$", message = "Phone must be 10 digits") String phone) {
    }
}
