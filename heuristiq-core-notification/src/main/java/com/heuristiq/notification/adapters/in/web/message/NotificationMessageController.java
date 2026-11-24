package com.railcab.nms.adapters.in.web.message;

import com.railcab.nms.adapters.in.web.WebApiConstants;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.nms.adapters.in.web.WebPaginationSupport;
import com.railcab.nms.application.port.in.NotificationMessageQueryUseCase;
import com.railcab.nms.domain.model.NotificationMessageStatus;
import com.railcab.nms.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.MESSAGES_PATH)
@RequiredArgsConstructor
@Tag(name = "Notification Messages", description = "Query idempotency ledger state")
public class NotificationMessageController {

    private final NotificationMessageQueryUseCase messageQueryService;

    @GetMapping
    @Operation(summary = "List notification message ledger records",
            description = "Lists idempotency ledger records, optionally filtered by status and/or correlation id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of ledger records returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> all(
            @Parameter(description = "Optional ledger status filter") @RequestParam(required = false) NotificationMessageStatus status,
            @Parameter(description = "Optional correlation id filter", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
            @RequestParam(required = false) String correlationId,
            @PageableDefault(size = 50) Pageable pageable) {
        var result = messageQueryService.list(status, correlationId, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }
}
