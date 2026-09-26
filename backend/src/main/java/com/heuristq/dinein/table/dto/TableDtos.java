package com.heuristq.dinein.table.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public final class TableDtos {

    private TableDtos() {
    }

    public record TableRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 _-]{1,20}$",
                    message = "1-20 letters, digits, space, dash or underscore") String label,
            Boolean active) {
    }

    public record TableResponse(Long id, String label, boolean active, String qrUrl, String qrImageUrl,
                                Instant createdAt, Instant updatedAt) {
    }
}
