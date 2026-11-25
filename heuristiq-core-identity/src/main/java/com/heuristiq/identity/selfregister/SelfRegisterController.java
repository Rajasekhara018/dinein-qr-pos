package com.heuristiq.identity.selfregister;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.auth.ClientIpResolver;
import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Public (no token) email verification for a sign-up. It lives under {@code identity/auth/**}, which the gateway treats as
 * public and rate limits per client address, and is deliberately not in the privilege catalogue.
 */
@RestController
@RequestMapping(SelfRegisterConstants.PUBLIC_BASE)
@Tag(name = "Self registration", description = "Verify an email address before creating an account.")
public class SelfRegisterController {
    private final SelfRegisterUseCase selfRegister;
    private final CommonRequestMapper requestMapper;

    public SelfRegisterController(SelfRegisterUseCase selfRegister, CommonRequestMapper requestMapper) {
        this.selfRegister = selfRegister;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Email a verification code",
            description = "Sends a six-digit code to the address. The answer is the same whether or not the address already has an account (its owner is told by email instead).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Done; a code or a notice is on its way"),
            @ApiResponse(responseCode = "400", description = "Validation failed, address not allowed, or bot check failed"),
            @ApiResponse(responseCode = "403", description = "Sign-up is switched off (SELF_REGISTRATION_DISABLED)"),
            @ApiResponse(responseCode = "429", description = "Too many requests (OTP_RATE_LIMITED)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SelfRegisterRequests.SendOtp.class)
    @PostMapping("/send-otp")
    public ResponseEntity<CommonResponseObject> sendOtp(HttpServletRequest http, @Valid @RequestBody CommonRequestObject commonRequest) {
        SelfRegisterRequests.SendOtp request = requestMapper.requireRequestObject(commonRequest, SelfRegisterRequests.SendOtp.class);
        return ResponseEntity.ok(CommonResponseObject.success(SelfRegisterConstants.MESSAGE_SENT,
                selfRegister.sendOtp(request.email(), request.applicationCode(), request.botToken(), ClientIpResolver.resolve(http))));
    }

    @Operation(summary = "Check the emailed code",
            description = "A right code is spent and a single-use verification token is returned; the sign-up form submits it with the application.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verified"),
            @ApiResponse(responseCode = "400", description = "Wrong, expired or used code (OTP_INVALID)"),
            @ApiResponse(responseCode = "403", description = "Sign-up is switched off (SELF_REGISTRATION_DISABLED)"),
            @ApiResponse(responseCode = "429", description = "Too many requests (OTP_RATE_LIMITED)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SelfRegisterRequests.VerifyOtp.class)
    @PostMapping("/verify-otp")
    public ResponseEntity<CommonResponseObject> verifyOtp(@Valid @RequestBody CommonRequestObject commonRequest) {
        SelfRegisterRequests.VerifyOtp request = requestMapper.requireRequestObject(commonRequest, SelfRegisterRequests.VerifyOtp.class);
        return ResponseEntity.ok(CommonResponseObject.success(selfRegister.verifyOtp(request.email(), request.otp(), request.applicationCode())));
    }
}
