package com.railcab.nms.adapters.in.web.device;

import com.railcab.nms.adapters.in.web.WebApiConstants;

import com.railcab.nms.domain.model.DeviceRegistration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.railcab.nms.application.port.in.DeviceTokenUseCase;
import com.railcab.nms.shared.CommonResponseObject;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(WebApiConstants.DEVICES_PATH)
@RequiredArgsConstructor
@Tag(name = "Device Tokens", description = "FCM device-token registration for the authenticated caller")
public class DeviceTokenController {

    private final DeviceTokenUseCase deviceTokenService;

    @Data
    public static class TokenRequest {
        @NotBlank(message = "token must not be blank")
        @Schema(description = "Opaque FCM device token issued by the client SDK", example = "fJ3x...:APA91bF...")
        private String token;

        @Schema(description = "Client platform the token was issued for", example = "android", defaultValue = "web")
        private String platform = "web";

        @Schema(description = "Which app install registered the token", example = "DRIVER_APP")
        private String app;

        @Schema(description = "Device model", example = "Pixel 8")
        private String deviceModel;

        @Schema(description = "Operating system and version", example = "Android 14")
        private String osVersion;

        @Schema(description = "App version and build", example = "1.4.2+17")
        private String appVersion;

        @Schema(description = "Device locale", example = "en_IN")
        private String locale;

        @Schema(description = "Device time zone", example = "Asia/Kolkata")
        private String timezone;
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > WebApiConstants.DEVICE_DETAIL_MAX_LENGTH ? trimmed.substring(0, WebApiConstants.DEVICE_DETAIL_MAX_LENGTH) : trimmed;
    }

    /** CUSTOMER / DRIVER / ... taken from the caller's own roles in the login token, never from the request body. */
    private static String userTypeOf(Authentication authentication) {
        java.util.Set<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", "").toUpperCase())
                .collect(java.util.stream.Collectors.toSet());
        if (roles.contains("DRIVER")) {
            return "DRIVER";
        }
        if (roles.contains("CUSTOMER")) {
            return "CUSTOMER";
        }
        return roles.stream().sorted().findFirst().orElse(null);
    }

    @PostMapping("/fcm-token")
    @Operation(summary = "Register an FCM device token for the caller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token registered"),
            @ApiResponse(responseCode = "400", description = "Missing/blank token, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> register(@Valid @RequestBody TokenRequest request, Authentication authentication) {
        deviceTokenService.register(authentication.getName(), request.getToken(), new DeviceRegistration(
                clip(request.getPlatform()),
                userTypeOf(authentication),
                clip(request.getApp()),
                clip(request.getDeviceModel()),
                clip(request.getOsVersion()),
                clip(request.getAppVersion()),
                clip(request.getLocale()),
                clip(request.getTimezone())));
        return ResponseEntity.ok(CommonResponseObject.success(null));
    }

    @DeleteMapping("/fcm-token")
    @Operation(summary = "Unregister an FCM device token for the caller")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token unregistered"),
            @ApiResponse(responseCode = "400", description = "Missing/blank token, or structured VALIDATION_ERROR"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<CommonResponseObject> unregister(@Valid @RequestBody TokenRequest request, Authentication authentication) {
        deviceTokenService.unregister(authentication.getName(), request.getToken());
        return ResponseEntity.ok(CommonResponseObject.success(null));
    }
}
