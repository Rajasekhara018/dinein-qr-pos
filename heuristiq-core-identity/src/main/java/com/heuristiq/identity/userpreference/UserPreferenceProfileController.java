package com.heuristiq.uam.userpreference;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.userpreference.UserPreference;
import com.heuristiq.uam.dto.UpdateMyPreferencesRequest;
import com.heuristiq.uam.dto.UpsertUserPreferenceRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service preferences for the caller's OWN account — separate from {@link UserPreferenceController}
 * (SUPER_ADMIN-only). The user id is always the JWT subject, never client-supplied input; any
 * {@code userId} in the request body is ignored and overwritten with the caller's own id before
 * reaching {@link UserPreferenceService}, so one user can never read/write another's preferences.
 */
@RestController
@RequestMapping({"/api/v1/uam/user-preferences/me", "/api/v1/uam/identity/me/preferences"})
@Tag(name = "User Preferences", description = "Self-service management of the authenticated caller's own UI/notification/dashboard/accessibility/regional preferences.")
public class UserPreferenceProfileController {
    private final UserPreferenceServiceUseCase preferenceService;
    private final CommonRequestMapper requestMapper;

    public UserPreferenceProfileController(UserPreferenceServiceUseCase preferenceService, CommonRequestMapper requestMapper) {
        this.preferenceService = preferenceService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Get my preferences", description = "Fetches the caller's own preference record, optionally scoped to a specific application. Returns an empty default record (not a 404) if none has been saved yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences returned (possibly empty defaults)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> getMine(Authentication authentication,
                                         @Parameter(description = "Restrict to preferences for this application") @RequestParam(required = false) String applicationId) {
        try {
            return ResponseEntity.ok(CommonResponseObject.success(preferenceService.getByUser(
                    new UserPreferenceServiceUseCase.GetByUserInput(authentication.getName(), applicationId)).preference()));
        } catch (UamException e) {
            // No preferences saved yet -- return an empty default rather than a 404-shaped failure.
            return ResponseEntity.ok(CommonResponseObject.success(UserPreference.builder()
                    .userId(authentication.getName())
                    .applicationId(applicationId)
                    .build()));
        }
    }

    @Operation(summary = "Update my preferences", description = "Upserts the caller's own preference record. The user id is always resolved server-side from the JWT, never client-supplied. Request payload is an UpdateMyPreferencesRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferences updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpdateMyPreferencesRequest.class)
    @PutMapping
    public ResponseEntity<CommonResponseObject> updateMine(Authentication authentication,
                                            @Valid @RequestBody CommonRequestObject commonRequest) {
        UpdateMyPreferencesRequest raw = requestMapper.requireRequestObject(commonRequest, UpdateMyPreferencesRequest.class);
        UpsertUserPreferenceRequest scoped = new UpsertUserPreferenceRequest(
                null, authentication.getName(), raw.applicationId(),
                raw.ui(), raw.notification(), raw.dashboard(), raw.accessibility(), raw.regional());
        return ResponseEntity.ok(CommonResponseObject.success(
                preferenceService.upsert(new UserPreferenceServiceUseCase.UpsertInput(scoped)).preference()));
    }
}
