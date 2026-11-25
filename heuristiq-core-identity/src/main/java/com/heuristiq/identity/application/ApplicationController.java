package com.heuristiq.identity.application;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApplicationConstants.BASE_PATH)
@Tag(name = "Applications", description = "The session organization's applications. Codes come from the fixed application catalogue.")
public class ApplicationController {
    private final ApplicationServiceUseCase applicationService;
    private final CommonRequestMapper requestMapper;

    public ApplicationController(ApplicationServiceUseCase applicationService, CommonRequestMapper requestMapper) {
        this.applicationService = applicationService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Register an application",
            description = "Creates an application in the session organization and links it to that organization. The code must be a "
                    + "catalogue code; name, description and display order default from the catalogue and the type is the catalogue's. "
                    + "The request payload is an UpsertApplicationRequest carried inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application created and linked to the session organization"),
            @ApiResponse(responseCode = "400", description = "Validation failed (field errors: code not in the catalogue, type other than the catalogue's)"),
            @ApiResponse(responseCode = "409", description = "The session organization already has this application, or its application limit is reached"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertApplicationRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest, Authentication authentication) {
        UpsertApplicationRequest request = requestMapper.requireRequestObject(commonRequest, UpsertApplicationRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                applicationService.create(new ApplicationServiceUseCase.CreateInput(request, actor(authentication))).application()));
    }

    @Operation(summary = "Update an application",
            description = "Replaces the editable fields of an application of the session organization. The code cannot change and the "
                    + "type stays the catalogue's. The request payload is an UpsertApplicationRequest carried inside the commonRequest.requestObject envelope.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed (field errors: code changed, type other than the catalogue's)"),
            @ApiResponse(responseCode = "403", description = "Name or description changed by someone who is not a platform administrator"),
            @ApiResponse(responseCode = "404", description = "Application not found in the session organization"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertApplicationRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Application id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest, Authentication authentication) {
        UpsertApplicationRequest request = requestMapper.requireRequestObject(commonRequest, UpsertApplicationRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                applicationService.update(new ApplicationServiceUseCase.UpdateInput(id, request, actor(authentication))).application()));
    }

    @Operation(summary = "Application catalogue",
            description = "Every application the platform offers, in display order, with its defaults and whether the session organization "
                    + "already has it (created). Covered by the APPLICATION_READ privilege.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping(ApplicationConstants.CATALOGUE_PATH)
    public ResponseEntity<CommonResponseObject> catalogue() {
        return ResponseEntity.ok(CommonResponseObject.success(applicationService.catalogue().items()));
    }

    @Operation(summary = "Get an application by id", description = "Fetches a single application's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application found"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Application id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                applicationService.get(new ApplicationServiceUseCase.GetInput(id)).application()));
    }

    @Operation(summary = "List applications", description = "Returns a page of the session organization's applications.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of applications returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by exact application code") @RequestParam(required = false) String code,
                               @Parameter(description = "Filter by application name (partial, case-insensitive)") @RequestParam(required = false) String name,
                               @Parameter(description = "Filter by exact application type") @RequestParam(required = false) ApplicationType type) {
        return ResponseEntity.ok(CommonResponseObject.success(
                applicationService.list(new ApplicationServiceUseCase.ListPageInput(page, size, code, name, type)).applications()));
    }

    @Operation(summary = "Delete an application",
            description = "Removes the application from the session organization (its link is cancelled); the application record is deleted "
                    + "too when no other organization uses it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application removed"),
            @ApiResponse(responseCode = "409", description = "Still in use: roles or users hold access, or other records reference it"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Application id") @PathVariable String id,
                                                       Authentication authentication) {
        applicationService.delete(new ApplicationServiceUseCase.DeleteInput(id, actor(authentication)));
        return ResponseEntity.ok(CommonResponseObject.success(ApplicationConstants.REMOVED, null));
    }

    private static String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
