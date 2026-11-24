package com.railcab.nms.adapters.in.web.event;

import com.railcab.nms.adapters.in.web.WebApiConstants;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.nms.application.port.in.EventDefinitionUseCase;
import com.railcab.nms.domain.model.NotificationEventDefinition;
import com.railcab.nms.shared.CommonRequestMapper;
import com.railcab.nms.shared.CommonRequestObject;
import com.railcab.nms.shared.CommonResponseObject;
import com.railcab.nms.shared.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.EVENTS_PATH)
@RequiredArgsConstructor
@Tag(name = "Events", description = "Configurable event definitions and their allowed template placeholders")
public class EventController {

    private final EventDefinitionUseCase eventService;
    private final CommonRequestMapper requestMapper;

    /** Returns all event keys + labels for the event dropdown. */
    @GetMapping
    @Operation(summary = "List active event definitions", description = "Returns all event keys + labels for the event dropdown.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active event definitions returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> listEvents() {
        return ResponseEntity.ok(CommonResponseObject.success("events", eventService.findAllActive()));
    }

    /** Creates a configurable event definition and its allowed placeholders. */
    @PostMapping
    @Operation(summary = "Create an event definition", description = "Creates a configurable event definition and its allowed placeholders.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Event created"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject, invalid field values, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(NotificationEventDefinition.class)
    public ResponseEntity<CommonResponseObject> create(
            @Valid @RequestBody CommonRequestObject commonRequest) {
        NotificationEventDefinition event = requestMapper.requireRequestObject(commonRequest, NotificationEventDefinition.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success("Event created", eventService.create(event)));
    }

    /** Updates an existing configurable event definition. */
    @PutMapping("/{id}")
    @Operation(summary = "Update an event definition", description = "Updates an existing configurable event definition.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event updated"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject, invalid field values, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "404", description = "Event not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(NotificationEventDefinition.class)
    public ResponseEntity<CommonResponseObject> update(
            @Parameter(description = "Event definition id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        NotificationEventDefinition event = requestMapper.requireRequestObject(commonRequest, NotificationEventDefinition.class);
        return ResponseEntity.ok(CommonResponseObject.success("Event updated", eventService.update(id, event)));
    }

    /** Soft-deletes an event definition. Existing templates are left untouched. */
    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate an event definition", description = "Soft-deletes an event definition. Existing templates are left untouched.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event deactivated"),
            @ApiResponse(responseCode = "400", description = "Event not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> delete(
            @Parameter(description = "Event definition id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id) {
        eventService.delete(id);
        return ResponseEntity.ok(CommonResponseObject.success("Event deactivated", null));
    }

    /** Returns the available template attributes for a given event. */
    @GetMapping("/{event}/attributes")
    @Operation(summary = "Get template attributes for an event", description = "Returns the available template placeholder attributes for a given event.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Attributes returned"),
            @ApiResponse(responseCode = "400", description = "Unknown event"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> getAttributes(
            @Parameter(description = "Event key", example = "RIDE_BOOKED") @PathVariable String event) {
        return ResponseEntity.ok(CommonResponseObject.success("attributes", eventService.attributesFor(event)));
    }

    /** Returns full config: each event with its label and attribute list. */
    @GetMapping("/config")
    @Operation(summary = "Get full event config", description = "Returns full config: each event with its label and attribute list.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event config returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> getConfig() {
        return ResponseEntity.ok(CommonResponseObject.success("config", eventService.config()));
    }

    /** Restores the default enum-backed event definitions. */
    @PostMapping("/reset-defaults")
    @Operation(summary = "Reset to default event definitions", description = "Restores the default enum-backed event definitions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Default events restored"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> resetDefaults() {
        return ResponseEntity.ok(CommonResponseObject.success("Default events restored", eventService.resetDefaults()));
    }
}
