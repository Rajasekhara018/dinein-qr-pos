package com.heuristiq.uam.application.access;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.common.UamException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uam/application-access")
@Tag(name = "Application access", description = "Which users, and which roles, may sign in to which registered application. One identity can hold access to many applications.")
public class ApplicationAccessController {
    private final ApplicationAccessServiceUseCase accessService;
    private final CommonRequestMapper requestMapper;

    public ApplicationAccessController(ApplicationAccessServiceUseCase accessService, CommonRequestMapper requestMapper) {
        this.accessService = accessService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Grant application access",
            description = "Lets a user, or everyone holding a role, sign in to an application. Re-activates a revoked grant. The request payload is a GrantApplicationAccessRequest carried inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Access granted"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Application or user not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(GrantApplicationAccessRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> grant(Authentication authentication,
                                                      @Valid @RequestBody CommonRequestObject commonRequest) {
        GrantApplicationAccessRequest request = requestMapper.requireRequestObject(commonRequest, GrantApplicationAccessRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                accessService.grant(new ApplicationAccessServiceUseCase.GrantInput(request, actor(authentication))).access()));
    }

    @Operation(summary = "Revoke application access",
            description = "Withdraws a grant. Sessions already issued keep working until their access token expires; the next sign-in or token refresh no longer includes the application.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access revoked"),
            @ApiResponse(responseCode = "404", description = "Grant not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> revoke(Authentication authentication,
                                                       @Parameter(description = "Grant id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                accessService.revoke(new ApplicationAccessServiceUseCase.RevokeInput(id, actor(authentication))).access()));
    }

    @Operation(summary = "List application access grants",
            description = "Give applicationCode to list every grant for one application, or subjectType and subjectId to list the grants held by one user or role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Grants returned"),
            @ApiResponse(responseCode = "400", description = "Neither applicationCode nor subjectType and subjectId was given"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(
            @Parameter(description = "Application code") @RequestParam(required = false) String applicationCode,
            @Parameter(description = "USER or ROLE") @RequestParam(required = false) AccessSubjectType subjectType,
            @Parameter(description = "User id or role name") @RequestParam(required = false) String subjectId) {
        if (applicationCode != null && !applicationCode.isBlank()) {
            return ResponseEntity.ok(CommonResponseObject.success(accessService.listForApplication(
                    new ApplicationAccessServiceUseCase.ListForApplicationInput(applicationCode)).grants()));
        }
        if (subjectType == null || subjectId == null || subjectId.isBlank()) {
            throw new UamException("Give applicationCode, or both subjectType and subjectId");
        }
        return ResponseEntity.ok(CommonResponseObject.success(accessService.listForSubject(
                new ApplicationAccessServiceUseCase.ListForSubjectInput(subjectType, subjectId)).grants()));
    }

    @Operation(summary = "Effective applications for a user",
            description = "The application codes the user can sign in to right now: direct grants plus grants to the user's roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application codes returned"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/users/{userId}/effective")
    public ResponseEntity<CommonResponseObject> effective(@Parameter(description = "User id") @PathVariable String userId) {
        return ResponseEntity.ok(CommonResponseObject.success(accessService.resolveAccessibleApplicationsForUserId(
                new ApplicationAccessServiceUseCase.ResolveForUserIdInput(userId)).applicationCodes()));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
