package com.heuristiq.uam.organization.membership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/uam/organizations/{tenantId}/members")
@Tag(name = "Organization members", description = "Who belongs to an organization. One account can belong to several organizations.")
public class TenantMembershipController {
    private final TenantMembershipServiceUseCase service;
    private final CommonRequestMapper requestMapper;

    public TenantMembershipController(TenantMembershipServiceUseCase service, CommonRequestMapper requestMapper) {
        this.service = service;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "List an organization's members", description = "Paged, newest first, with the member's user name and email.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Members returned"),
            @ApiResponse(responseCode = "404", description = "Organization not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Organization id") @PathVariable String tenantId,
                                                     @Parameter(description = "Only active members whose account matches this text") @RequestParam(required = false) String q,
                                                     @PageableDefault(size = 20, sort = "joinedAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        Page<MemberView> page = service.listMembers(new TenantMembershipServiceUseCase.ListInput(tenantId, pageable, q));
        return ResponseEntity.ok(CommonResponseObject.success(page));
    }

    @Operation(summary = "Add a member", description = "Makes an existing account a member, or turns a removed or paused membership back on. The payload is an AddMemberRequest inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Member added"),
            @ApiResponse(responseCode = "404", description = "Organization or user not found"),
            @ApiResponse(responseCode = "409", description = "The organization has reached its member limit"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(AddMemberRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> add(Authentication authentication,
                                                    @Parameter(description = "Organization id") @PathVariable String tenantId,
                                                    @Valid @RequestBody CommonRequestObject commonRequest) {
        AddMemberRequest request = requestMapper.requireRequestObject(commonRequest, AddMemberRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(service.addMember(
                new TenantMembershipServiceUseCase.AddInput(tenantId, request, actor(authentication)))));
    }

    @Operation(summary = "Change a member's standing", description = "ACTIVE, SUSPENDED or REMOVED. A removed member is kept in the history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "The person is not a member"),
            @ApiResponse(responseCode = "409", description = "The organization has reached its member limit"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpdateMemberRequest.class)
    @PutMapping("/{userId}")
    public ResponseEntity<CommonResponseObject> update(Authentication authentication,
                                                       @Parameter(description = "Organization id") @PathVariable String tenantId,
                                                       @Parameter(description = "Account id") @PathVariable String userId,
                                                       @Valid @RequestBody CommonRequestObject commonRequest) {
        UpdateMemberRequest request = requestMapper.requireRequestObject(commonRequest, UpdateMemberRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(service.updateMember(
                new TenantMembershipServiceUseCase.UpdateInput(tenantId, userId, request, actor(authentication)))));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
