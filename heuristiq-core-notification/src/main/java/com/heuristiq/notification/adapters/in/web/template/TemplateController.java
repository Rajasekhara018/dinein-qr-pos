package com.heuristiq.notification.adapters.in.web.template;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.notification.adapters.in.web.WebPaginationSupport;
import com.heuristiq.notification.application.dto.TemplateTestSendRequest;
import com.heuristiq.notification.application.port.in.TemplateTestSendUseCase;
import com.heuristiq.notification.application.port.in.TemplateUseCase;
import com.heuristiq.notification.application.security.NmsPrincipal;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.shared.CommonRequestMapper;
import com.heuristiq.notification.shared.CommonRequestObject;
import com.heuristiq.notification.shared.CommonResponseObject;
import com.heuristiq.notification.shared.NmsNotFoundException;
import com.heuristiq.notification.shared.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping(WebApiConstants.TEMPLATES_PATH)
@RequiredArgsConstructor
@Tag(name = "Templates", description = "CRUD for notification templates (one per event × channel)")
public class TemplateController {

    private final TemplateUseCase templateService;
    private final CommonRequestMapper requestMapper;
    private final TemplateTestSendUseCase testSendService;
    private final Validator validator;

    @PostMapping
    @Operation(summary = "Create a new notification template")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Template created"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject, invalid field values, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(NotificationTemplate.class)
    public ResponseEntity<CommonResponseObject> create(
            @Valid @RequestBody CommonRequestObject commonRequest) {
        NotificationTemplate template = requestMapper.requireRequestObject(commonRequest, NotificationTemplate.class);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponseObject.success("Template created", templateService.create(template)));
    }

    @PostMapping("/test-send")
    @Operation(summary = "Send one test message from the template editor",
            description = "Renders the unsaved template content with the given sample values and sends it once, marked [TEST], "
                    + "to the typed recipient. EMAIL, SMS and WHATSAPP only; rate limited per user. Nothing is stored.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider answered; see sent=true/false in the response"),
            @ApiResponse(responseCode = "400", description = "Channel not testable, recipient invalid, or validation error"),
            @ApiResponse(responseCode = "429", description = "Too many test messages in the last hour"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(TemplateTestSendRequest.class)
    public ResponseEntity<CommonResponseObject> testSend(
            @Valid @RequestBody CommonRequestObject commonRequest) {
        TemplateTestSendRequest request = requestMapper.requireRequestObject(commonRequest, TemplateTestSendRequest.class);
        var violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        NmsPrincipal principal = auth != null && auth.getPrincipal() instanceof NmsPrincipal p ? p : null;
        String callerId = principal == null ? null : principal.userId();
        String tenantId = principal == null ? null : principal.tenantId();
        return ResponseEntity.ok(CommonResponseObject.success(testSendService.send(callerId, tenantId, request)));
    }

    @GetMapping
    @Operation(summary = "List all active templates")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of templates returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> listAll(
            @PageableDefault(size = 50) Pageable pageable) {
        var result = templateService.findAll(WebPaginationSupport.toPageQuery(pageable));
        return ResponseEntity.ok(CommonResponseObject.success(WebPaginationSupport.toPage(result, pageable)));
    }

    @GetMapping("/event/{event}")
    @Operation(summary = "List active templates for an event (all channels)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Templates for the event returned"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byEvent(
            @Parameter(description = "Event key", example = "RIDE_BOOKED") @PathVariable String event) {
        return ResponseEntity.ok(CommonResponseObject.success(templateService.findByEvent(event)));
    }

    @GetMapping("/event/{event}/channel/{channel}")
    @Operation(summary = "Fetch the active template for a specific event × channel")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template returned"),
            @ApiResponse(responseCode = "404", description = "No active template found for the event/channel pair"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> byEventAndChannel(
            @Parameter(description = "Event key", example = "RIDE_BOOKED") @PathVariable String event,
            @Parameter(description = "Notification channel") @PathVariable NotificationChannel channel) {
        return templateService.findByEventAndChannel(event, channel)
                .map(t -> ResponseEntity.ok(CommonResponseObject.success(t)))
                .orElseThrow(() -> new NmsNotFoundException(
                        "No active template found for " + event + " / " + channel));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get template by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template returned"),
            @ApiResponse(responseCode = "404", description = "Template not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> getById(
            @Parameter(description = "Template id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id) {
        return templateService.findById(id)
                .map(t -> ResponseEntity.ok(CommonResponseObject.success(t)))
                .orElseThrow(() -> new NmsNotFoundException("Template not found"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing template")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template updated"),
            @ApiResponse(responseCode = "400", description = "Missing requestObject, invalid field values, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "404", description = "Template not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @RequestObjectType(NotificationTemplate.class)
    public ResponseEntity<CommonResponseObject> update(
            @Parameter(description = "Template id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id,
            @Valid @RequestBody CommonRequestObject commonRequest) {
        NotificationTemplate template = requestMapper.requireRequestObject(commonRequest, NotificationTemplate.class);
        return ResponseEntity.ok(CommonResponseObject.success("Template updated", templateService.update(id, template)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete (deactivate) a template")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Template deactivated"),
            @ApiResponse(responseCode = "400", description = "Template not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> delete(
            @Parameter(description = "Template id", example = "6620e1f2a1b2c3d4e5f6a7b8") @PathVariable String id) {
        templateService.delete(id);
        return ResponseEntity.ok(CommonResponseObject.success("Template deactivated", null));
    }
}
