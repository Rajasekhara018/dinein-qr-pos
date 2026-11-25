package com.heuristiq.notification.adapters.in.web.log;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.notification.adapters.in.web.WebPaginationSupport;
import com.heuristiq.notification.application.port.in.NotificationAttemptQueryUseCase;
import com.heuristiq.notification.domain.repository.PageResult;
import com.heuristiq.notification.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Failure analysis: every delivery attempt of a message, origin to destination. Access is decided
 * by the gateway's privilege catalog (NMS_LOG_ATTEMPTS_BY_MESSAGE_READ and
 * NMS_LOG_ATTEMPTS_BY_CORRELATION_READ); the existing {@code /logs} endpoints are unchanged.
 */
@RestController
@RequestMapping(WebApiConstants.LOG_ATTEMPTS_PATH)
@RequiredArgsConstructor
@Tag(name = "Notification Logs", description = "Query dispatch history and delivery stats")
public class NotificationAttemptController {

    private final NotificationAttemptQueryUseCase attempts;

    @GetMapping("/message/{messageId}")
    @Operation(summary = "All delivery attempts of one message (oldest first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of attempts returned (empty when the message id is unknown)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byMessageId(
            @Parameter(description = "Message id (idempotency key) of the notification", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
            @PathVariable String messageId,
            @PageableDefault(size = 50) Pageable pageable) {
        PageResult<com.heuristiq.notification.domain.model.NotificationAttempt> result =
                attempts.findByMessageId(messageId, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(
                WebPaginationSupport.toPage(map(result), pageable)));
    }

    @GetMapping("/correlation/{correlationId}")
    @Operation(summary = "All delivery attempts that share a correlation id (oldest first)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of attempts returned (empty when the correlation id is unknown)"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byCorrelationId(
            @Parameter(description = "Correlation id shared by all notifications from one dispatch", example = "9b1c2e2e-1f3a-4e9b-8b3a-8f1a2c3d4e5f")
            @PathVariable String correlationId,
            @PageableDefault(size = 50) Pageable pageable) {
        PageResult<com.heuristiq.notification.domain.model.NotificationAttempt> result =
                attempts.findByCorrelationId(correlationId, WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(
                WebPaginationSupport.toPage(map(result), pageable)));
    }

    private static PageResult<NotificationAttemptResponse> map(
            PageResult<com.heuristiq.notification.domain.model.NotificationAttempt> page) {
        return new PageResult<>(page.content().stream().map(NotificationAttemptResponse::from).toList(),
                page.page(), page.size(), page.totalElements());
    }
}
