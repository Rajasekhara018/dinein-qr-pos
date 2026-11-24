package com.railcab.nms.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class NotificationEventDefinition {

    @Schema(description = "Event definition id (absent on create)", example = "6620e1f2a1b2c3d4e5f6a7b8")
    private String id;

    @NotBlank(message = "value must not be blank")
    @Schema(description = "Event key used to look up templates and trigger dispatches", example = "RIDE_BOOKED")
    private String value;

    @NotBlank(message = "label must not be blank")
    @Schema(description = "Human-readable label for the event dropdown", example = "Ride Booked")
    private String label;

    @Schema(description = "Allowed template placeholder attributes for this event")
    private List<String> attributes = new ArrayList<>();

    @Schema(description = "Whether the event definition is active", example = "true")
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
