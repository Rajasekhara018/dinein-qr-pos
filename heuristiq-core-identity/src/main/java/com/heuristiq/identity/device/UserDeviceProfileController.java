package com.heuristiq.identity.device;

import org.springframework.http.HttpHeaders;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import com.heuristiq.identity.common.UamException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service device access — see {@link com.heuristiq.identity.users.UserProfileController}'s javadoc for the pattern.
 * {@link UserDeviceService}'s id-based methods (get/delete/trust/block) have no ownership check,
 * so every action here verifies {@code device.getUserId()} against the caller first.
 */
@RestController
@RequestMapping({"/api/v1/uam/users/me/devices", "/api/v1/uam/identity/me/devices"})
@Tag(name = "User Devices", description = "Self-service management of devices registered to the authenticated caller's own account.")
public class UserDeviceProfileController {
    private final UserDeviceServiceUseCase deviceService;
    private final DeviceLocationService deviceLocationService;

    public UserDeviceProfileController(UserDeviceServiceUseCase deviceService, DeviceLocationService deviceLocationService) {
        this.deviceService = deviceService;
        this.deviceLocationService = deviceLocationService;
    }

    /** Body of PUT /current/location. */
    public record DeviceLocationRequest(Double latitude, Double longitude, Double accuracyMeters,
                                        String city, String state, String country) {
    }

    @Operation(summary = "Record where my current device is",
            description = "Stores the location the caller's own browser reported (latitude / longitude, optional accuracy and city/state/country labels) on the device making this request and on its active sessions, so it shows in Active devices. The device is identified by its User-Agent, as at login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Location recorded"),
            @ApiResponse(responseCode = "400", description = "Invalid coordinates, or no device registered for this browser")
    })
    @PutMapping("/current/location")
    public CommonResponseObject recordLocation(Authentication authentication, HttpServletRequest request,
                                               @RequestBody DeviceLocationRequest body) {
        UserDevice device = deviceLocationService.record(authentication.getName(), request.getHeader(HttpHeaders.USER_AGENT),
                new DeviceLocationService.LocationInput(body.latitude(), body.longitude(), body.accuracyMeters(),
                        body.city(), body.state(), body.country()));
        return CommonResponseObject.success(device);
    }

    @Operation(summary = "List my devices", description = "Returns every device registered to the caller's own account. Requires authentication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public CommonResponseObject listMine(Authentication authentication) {
        return CommonResponseObject.success(
                deviceService.listByUser(new UserDeviceServiceUseCase.ListByUserInput(authentication.getName())).devices());
    }

    @Operation(summary = "Set my device's trusted flag",
            description = "Marks one of the caller's own devices as trusted or untrusted. Rejects the request if the device does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device trust flag updated"),
            @ApiResponse(responseCode = "400", description = "Device not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/trust")
    public CommonResponseObject trustMine(Authentication authentication, @Parameter(description = "Device id") @PathVariable String id,
                                           @Parameter(description = "Whether the device should be marked trusted") @RequestParam boolean trusted) {
        assertOwned(authentication, id);
        return CommonResponseObject.success(
                deviceService.trust(new UserDeviceServiceUseCase.TrustInput(id, trusted)).device());
    }

    @Operation(summary = "Block or unblock my device",
            description = "Blocks (with an optional reason) or unblocks one of the caller's own devices. Rejects the request if the device does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device block state updated"),
            @ApiResponse(responseCode = "400", description = "Device not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/block")
    public CommonResponseObject blockMine(Authentication authentication, @Parameter(description = "Device id") @PathVariable String id,
                                           @Parameter(description = "Whether to block (true) or unblock (false) the device") @RequestParam boolean blocked,
                                           @Parameter(description = "Reason for blocking, if blocking") @RequestParam(required = false) String reason) {
        assertOwned(authentication, id);
        UserDevice updated = blocked
                ? deviceService.block(new UserDeviceServiceUseCase.BlockInput(id, reason)).device()
                : deviceService.unblock(new UserDeviceServiceUseCase.UnblockInput(id)).device();
        return CommonResponseObject.success(updated);
    }

    @Operation(summary = "Remove my device", description = "Permanently deregisters one of the caller's own devices. Rejects the request if the device does not belong to the caller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device removed"),
            @ApiResponse(responseCode = "400", description = "Device not found or not owned by the caller"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public CommonResponseObject removeMine(Authentication authentication, @Parameter(description = "Device id") @PathVariable String id) {
        assertOwned(authentication, id);
        deviceService.delete(new UserDeviceServiceUseCase.DeleteInput(id));
        return CommonResponseObject.success(OperationResult.completed("DELETE", "USER_DEVICE", id));
    }

    private void assertOwned(Authentication authentication, String deviceId) {
        UserDevice device = deviceService.get(new UserDeviceServiceUseCase.GetInput(deviceId)).device();
        if (!authentication.getName().equals(device.getUserId())) {
            throw new UamException("Device not found");
        }
    }
}
