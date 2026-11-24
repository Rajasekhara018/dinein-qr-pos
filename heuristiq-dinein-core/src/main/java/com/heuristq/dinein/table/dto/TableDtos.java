package com.heuristq.dinein.table.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TableDtos {

    private TableDtos() {
    }

    public record TableRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9 _-]{1,20}$",
                    message = "1-20 letters, digits, space, dash or underscore") String label,
            Boolean active) {
    }

    public record TableResponse(Long id, String label, boolean active, boolean occupied, boolean reserved,
                                Instant reservedUntil, String reservedNote, String qrUrl, String qrImageUrl,
                                Instant createdAt, Instant updatedAt) {
    }

    public record ReserveRequest(@NotNull @Future Instant until, @Size(max = 100) String note) {
    }

    /** Which table an order's items move to (see {@code TableService#moveOrder}). */
    public record MoveOrderRequest(@NotNull Long tableId) {
    }

    /** Every open order on {@code fromTableId} moves to {@code toTableId}; {@code fromTableId} ends up free. */
    public record MergeTablesRequest(@NotNull Long fromTableId, @NotNull Long toTableId) {
    }
}
