package com.railcab.nms.adapters.in.web.inapp;

import com.railcab.nms.adapters.in.web.WebApiConstants;

import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.nms.adapters.in.web.WebPaginationSupport;
import com.railcab.nms.application.port.in.InAppNotificationUseCase;
import com.railcab.nms.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.IN_APP_NOTIFICATIONS_PATH)
@RequiredArgsConstructor
@Tag(name = "In-App Notifications", description = "Read/mark-read for the authenticated caller's own in-app notifications")
public class InAppNotificationController {

    private final InAppNotificationUseCase inAppNotificationService;

    @GetMapping
    @Operation(summary = "Full notification history for the caller (newest first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of in-app notifications returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> list(Authentication authentication, @PageableDefault(size = 20) Pageable pageable) {
        var result = inAppNotificationService.list(authentication.getName(), WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count for the caller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> unreadCount(Authentication authentication) {
        long count = inAppNotificationService.unreadCount(authentication.getName());
        return ResponseEntity.ok(CommonResponseObject.success(Map.of("unreadCount", count)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "400", description = "Notification not found, or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> markAsRead(
            @Parameter(description = "In-app notification id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id,
            Authentication authentication) {
        inAppNotificationService.markAsRead(id, authentication.getName());
        return ResponseEntity.ok(CommonResponseObject.success(null));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of the caller's notifications as read")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "All notifications marked as read"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> markAllAsRead(Authentication authentication) {
        inAppNotificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(CommonResponseObject.success(null));
    }
}
