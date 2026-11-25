package com.heuristiq.identity.selfregister;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Service-to-service only: reachable with the shared {@code X-Rider-Service-Token} (see InternalServiceSecurityConfig),
 * never with an end-user token, and not exposed through the public gateway catalogue.
 */
@RestController
@RequestMapping(SelfRegisterConstants.INTERNAL_BASE)
@Tag(name = "Self registration (internal)", description = "Spend an email verification token when a sign-up is submitted.")
public class InternalSelfRegisterController {
    private final SelfRegisterUseCase selfRegister;
    private final CommonRequestMapper requestMapper;

    public InternalSelfRegisterController(SelfRegisterUseCase selfRegister, CommonRequestMapper requestMapper) {
        this.selfRegister = selfRegister;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Email the set-your-password code",
            description = "Sends a newly created member the one-time code they use with reset-password to choose their own password. Best effort: always answers 200.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Done (a code may or may not have been sent)"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SelfRegisterRequests.SendPasswordCode.class)
    @PostMapping("/send-password-code")
    public ResponseEntity<CommonResponseObject> sendPasswordCode(@Valid @RequestBody CommonRequestObject commonRequest) {
        SelfRegisterRequests.SendPasswordCode request = requestMapper.requireRequestObject(commonRequest, SelfRegisterRequests.SendPasswordCode.class);
        selfRegister.sendSetPasswordCode(request.email());
        return ResponseEntity.ok(CommonResponseObject.success(null));
    }

    @Operation(summary = "Spend a verification token",
            description = "Answers verified=true exactly once for a token issued for this email and application that has not expired; anything else is verified=false.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Answered"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or wrong service token"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SelfRegisterRequests.ConsumeToken.class)
    @PostMapping("/verify-token")
    public ResponseEntity<CommonResponseObject> verifyToken(@Valid @RequestBody CommonRequestObject commonRequest) {
        SelfRegisterRequests.ConsumeToken request = requestMapper.requireRequestObject(commonRequest, SelfRegisterRequests.ConsumeToken.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                new SelfRegisterRequests.Consumed(selfRegister.consumeToken(request.email(), request.token(), request.applicationCode()))));
    }
}
