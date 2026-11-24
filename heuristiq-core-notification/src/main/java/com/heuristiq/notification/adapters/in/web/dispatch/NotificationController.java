package com.railcab.nms.adapters.in.web.dispatch;

import com.railcab.nms.adapters.in.web.WebApiConstants;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.nms.adapters.in.OriginHeaders;
import com.railcab.nms.application.dto.NotificationDispatchResult;
import com.railcab.nms.application.dto.NotificationRequest;
import com.railcab.nms.application.port.in.NotificationDispatchUseCase;
import com.railcab.nms.shared.CommonRequestMapper;
import com.railcab.nms.shared.CommonRequestObject;
import com.railcab.nms.shared.CommonResponseObject;
import com.railcab.nms.shared.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.NOTIFY_PATH)
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Trigger event-based multi-channel notifications")
public class NotificationController {

    private final NotificationDispatchUseCase dispatchService;
    private final CommonRequestMapper requestMapper;

    /**
     * Fire a notification event.
     *
     * <p>The service loads the active template per channel for the event,
     * renders {{variable}} placeholders, and dispatches across all configured channels.
     */
    @PostMapping
    @Operation(summary = "Trigger a notification (event + variables + recipients)",
            description = "Wraps a NotificationRequest in the common request envelope. Loads the active template per "
                    + "enabled channel for the event, renders {{variable}} placeholders, and dispatches to the matching recipients.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "At least one channel dispatched successfully, or all channels failed (success=false in the envelope)"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject/event, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(NotificationRequest.class)
    public ResponseEntity<CommonResponseObject> notify(
            @Valid @RequestBody CommonRequestObject commonRequest,
            @RequestHeader(value = OriginHeaders.SOURCE_SERVICE, required = false) String sourceServiceHeader) {
        NotificationRequest request = requestMapper.requireRequestObject(commonRequest, NotificationRequest.class);
        if (request.getMessageId() == null || request.getMessageId().isBlank()) {
            request.setMessageId(commonRequest.getMessageID());
        }
        if (!StringUtils.hasText(request.getCorrelationId())) {
            request.setCorrelationId(commonRequest.getMessageID());
        }
        // Back-compatible origin: a caller that cannot change its payload yet can name itself in a header.
        if (!StringUtils.hasText(request.getSourceService()) && StringUtils.hasText(sourceServiceHeader)) {
            request.setSourceService(sourceServiceHeader.trim());
        }
        List<NotificationDispatchResult> results = dispatchService.dispatch(request);
        boolean anyOk = results.stream().anyMatch(NotificationDispatchResult::success);
        return ResponseEntity.ok(anyOk
                ? CommonResponseObject.success("Notification dispatched", results)
                : CommonResponseObject.failure("All channels failed"));
    }
}
