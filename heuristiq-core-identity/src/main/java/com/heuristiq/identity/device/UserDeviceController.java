package com.heuristiq.uam.device;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam")
@Tag(name = "User Devices", description = "Administrative management of devices registered to user accounts (trust/block/remove).")
public class UserDeviceController {
    private final UserDeviceServiceUseCase deviceService;

    public UserDeviceController(UserDeviceServiceUseCase deviceService) {
        this.deviceService = deviceService;
    }

    @Operation(summary = "List a user's devices", description = "Returns every device registered to the given user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Devices returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/users/{userId}/devices")
    public CommonResponseObject listByUser(@Parameter(description = "User id") @PathVariable String userId) {
        return CommonResponseObject.success(
                deviceService.listByUser(new UserDeviceServiceUseCase.ListByUserInput(userId)).devices());
    }

    @Operation(summary = "Get a device by id", description = "Fetches a single registered device's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device found"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/devices/{id}")
    public CommonResponseObject get(@Parameter(description = "Device id") @PathVariable String id) {
        return CommonResponseObject.success(
                deviceService.get(new UserDeviceServiceUseCase.GetInput(id)).device());
    }

    @Operation(summary = "Remove a device", description = "Permanently deregisters a device from its owning user's account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device removed"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/devices/{id}")
    public CommonResponseObject delete(@Parameter(description = "Device id") @PathVariable String id) {
        deviceService.delete(new UserDeviceServiceUseCase.DeleteInput(id));
        return CommonResponseObject.success(OperationResult.completed("DELETE", "USER_DEVICE", id));
    }

    @Operation(summary = "Set a device's trusted flag", description = "Marks a device as trusted or untrusted, e.g. to skip/require step-up MFA on future logins from it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device trust flag updated"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/devices/{id}/trust")
    public CommonResponseObject trust(@Parameter(description = "Device id") @PathVariable String id,
                                       @Parameter(description = "Whether the device should be marked trusted") @RequestParam(defaultValue = "true") boolean trusted) {
        return CommonResponseObject.success(
                deviceService.trust(new UserDeviceServiceUseCase.TrustInput(id, trusted)).device());
    }

    @Operation(summary = "Block or unblock a device", description = "Blocks a device (with an optional reason) to prevent it from being used to authenticate, or unblocks it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device block state updated"),
            @ApiResponse(responseCode = "404", description = "Device not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/devices/{id}/block")
    public CommonResponseObject block(@Parameter(description = "Device id") @PathVariable String id,
                                @Parameter(description = "Whether to block (true) or unblock (false) the device") @RequestParam(defaultValue = "true") boolean blocked,
                                @Parameter(description = "Reason for blocking, if blocking") @RequestParam(required = false) String reason) {
        return CommonResponseObject.success(blocked
                ? deviceService.block(new UserDeviceServiceUseCase.BlockInput(id, reason)).device()
                : deviceService.unblock(new UserDeviceServiceUseCase.UnblockInput(id)).device());
    }
}
