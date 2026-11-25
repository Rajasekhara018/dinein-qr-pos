package com.heuristiq.notification.adapters.in.web.broadcast;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.notification.adapters.in.web.WebPaginationSupport;
import com.heuristiq.notification.application.dto.CreateBroadcastRequest;
import com.heuristiq.notification.application.port.in.BroadcastUseCase;
import com.heuristiq.notification.shared.CommonRequestMapper;
import com.heuristiq.notification.shared.CommonRequestObject;
import com.heuristiq.notification.shared.CommonResponseObject;
import com.heuristiq.notification.shared.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.BROADCASTS_PATH)
@RequiredArgsConstructor
@Tag(name = "Broadcasts", description = "Admin-composed broadcast jobs — scheduling and audience targeting on top of /api/v1/nms/notify")
public class BroadcastController {

    private final BroadcastUseCase broadcastService;
    private final CommonRequestMapper requestMapper;

    @PostMapping
    @Operation(summary = "Compose (and optionally schedule) a broadcast",
            description = "Wraps a CreateBroadcastRequest (event, segment, channels, optional scheduledAt) in the common request envelope. "
                    + "Null/past scheduledAt dispatches on the next sweep; a future value holds it until then.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Broadcast created"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject, invalid field values, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(CreateBroadcastRequest.class)
    public ResponseEntity<CommonResponseObject> create(
            @Valid @RequestBody CommonRequestObject commonRequest,
            Authentication authentication) {
        CreateBroadcastRequest request = requestMapper.requireRequestObject(commonRequest, CreateBroadcastRequest.class);
        String createdBy = authentication != null ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success("Broadcast created", broadcastService.create(request, createdBy)));
    }

    @GetMapping
    @Operation(summary = "List broadcasts, most recent first")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of broadcasts returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> list(@PageableDefault(size = 20) Pageable pageable) {
        var result = broadcastService.list(WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a broadcast by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Broadcast returned"),
            @ApiResponse(responseCode = "404", description = "Broadcast not found for the given id"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> get(
            @Parameter(description = "Broadcast id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(broadcastService.get(id)));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending broadcast")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Broadcast cancelled"),
            @ApiResponse(responseCode = "400", description = "Broadcast not in a cancellable state"),
            @ApiResponse(responseCode = "404", description = "Broadcast not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> cancel(
            @Parameter(description = "Broadcast id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success("Broadcast cancelled", broadcastService.cancel(id)));
    }
}
