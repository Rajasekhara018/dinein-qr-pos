package com.heuristq.dinein.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class KitchenStationDtos {

    private KitchenStationDtos() {
    }

    public record KitchenStationRequest(@NotBlank @Size(max = 40) String name, Boolean active) {
    }

    public record KitchenStationResponse(Long id, String name, int displayOrder, boolean active) {
    }
}
