package com.heuristiq.notification.adapters.in.web.log;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

import java.time.LocalDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.notification.adapters.in.web.WebPaginationSupport;
import com.heuristiq.notification.application.port.in.NotificationLogUseCase;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.LOGS_PATH)
@RequiredArgsConstructor
@Tag(name = "Notification Logs", description = "Query dispatch history and delivery stats")
public class NotificationLogController {

    private final NotificationLogUseCase logService;

    @GetMapping
    @Operation(summary = "List all notification logs (most recent first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of notification logs returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> all(
            @PageableDefault(size = 50) Pageable pageable) {
        var result = logService.findAll(WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/event")
    @Operation(summary = "Filter logs by event type")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching notification logs returned"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid event parameter"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byEvent(
            @Parameter(description = "Event key to filter by", example = "RIDE_BOOKED") @RequestParam String event,
            @PageableDefault(size = 50) Pageable pageable) {
        var result = logService.findByEvent(event, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/correlation/{correlationId}")
    @Operation(summary = "Filter logs by correlation ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching notification logs returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byCorrelationId(
            @Parameter(description = "Correlation id shared by all notifications from one dispatch", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
            @PathVariable String correlationId,
            @PageableDefault(size = 50) Pageable pageable) {
        var result = logService.findByCorrelationId(correlationId, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/channel")
    @Operation(summary = "Filter logs by channel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching notification logs returned"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid channel parameter"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byChannel(
            @Parameter(description = "Notification channel to filter by") @RequestParam NotificationChannel channel,
            @PageableDefault(size = 50) Pageable pageable) {
        var result = logService.findByChannel(channel, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/range")
    @Operation(summary = "Filter logs by sent timestamp range")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of matching notification logs returned"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid from or to timestamp"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byRange(
            @Parameter(description = "Range start (inclusive), ISO-8601 date-time", example = "2026-07-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Range end (exclusive), ISO-8601 date-time", example = "2026-07-18T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 50) Pageable pageable) {
        var result = logService.findBySentAtBetween(from, to, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Success / failure counts per channel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Per-channel stats returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> stats() {
        return ResponseEntity.ok(CommonResponseObject.success(logService.stats()));
    }
}
