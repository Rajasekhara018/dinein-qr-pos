package com.heuristiq.identity.userpreference;

import com.heuristiq.identity.common.CommonRequestMapper;
import com.heuristiq.identity.common.CommonRequestObject;
import com.heuristiq.identity.common.CommonResponseObject;
import com.heuristiq.identity.common.OperationResult;
import com.heuristiq.identity.common.RequestObjectType;
import com.heuristiq.identity.dto.UpsertUserPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/uam/user-preferences")
@Tag(name = "User Preferences", description = "Administrative management of any user's UI/notification/dashboard/accessibility/regional preferences.")
public class UserPreferenceController {
    private final UserPreferenceServiceUseCase preferenceService;
    private final CommonRequestMapper requestMapper;

    public UserPreferenceController(UserPreferenceServiceUseCase preferenceService, CommonRequestMapper requestMapper) {
        this.preferenceService = preferenceService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create a user's preferences", description = "Creates (upserts) a preference record for a user. Request payload is an UpsertUserPreferenceRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Preference record created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertUserPreferenceRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertUserPreferenceRequest request = requestMapper.requireRequestObject(commonRequest, UpsertUserPreferenceRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                preferenceService.upsert(new UserPreferenceServiceUseCase.UpsertInput(request)).preference()));
    }

    @Operation(summary = "Update a user's preferences", description = "Upserts the mutable fields of an existing preference record. Request payload is an UpsertUserPreferenceRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preference record updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Preference record not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertUserPreferenceRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "Preference record id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertUserPreferenceRequest request = requestMapper.requireRequestObject(commonRequest, UpsertUserPreferenceRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                preferenceService.upsert(new UserPreferenceServiceUseCase.UpsertInput(request)).preference()));
    }

    @Operation(summary = "Get a preference record by id", description = "Fetches a single preference record's details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preference record found"),
            @ApiResponse(responseCode = "404", description = "Preference record not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "Preference record id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                preferenceService.get(new UserPreferenceServiceUseCase.GetInput(id)).preference()));
    }

    @Operation(summary = "Get a user's preferences", description = "Fetches a user's preference record, optionally scoped to a specific application.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preference record found"),
            @ApiResponse(responseCode = "404", description = "No preference record for this user/application"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<CommonResponseObject> getByUser(@Parameter(description = "User id") @PathVariable String userId,
                                    @Parameter(description = "Restrict to preferences for this application") @RequestParam(required = false) String applicationId) {
        return ResponseEntity.ok(CommonResponseObject.success(
                preferenceService.getByUser(new UserPreferenceServiceUseCase.GetByUserInput(userId, applicationId)).preference()));
    }

    @Operation(summary = "List preference records", description = "Returns a page of user preference records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of preference records returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(CommonResponseObject.success(
                preferenceService.list(new UserPreferenceServiceUseCase.ListPageInput(page, size)).preferences()));
    }

    @Operation(summary = "Delete a preference record", description = "Permanently removes a user's preference record.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Preference record deleted"),
            @ApiResponse(responseCode = "404", description = "Preference record not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "Preference record id") @PathVariable String id) {
        preferenceService.delete(new UserPreferenceServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
