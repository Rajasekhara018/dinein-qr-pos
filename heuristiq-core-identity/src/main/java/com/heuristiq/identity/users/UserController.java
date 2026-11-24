package com.heuristiq.uam.users;

import com.heuristiq.uam.common.CommonRequestMapper;
import com.heuristiq.uam.common.CommonRequestObject;
import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.OperationResult;
import com.heuristiq.uam.common.RequestObjectType;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.dto.SetPasswordRequest;
import com.heuristiq.uam.dto.UpsertUserRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uam/users")
@Tag(name = "User Management", description = "Administrative CRUD and account-management operations on UAM user accounts (any UserType). Not for self-service -- see the /users/me endpoints for that.")
public class UserController {
    private final UamUserServiceUseCase userService;
    private final CommonRequestMapper requestMapper;

    public UserController(UamUserServiceUseCase userService, CommonRequestMapper requestMapper) {
        this.userService = userService;
        this.requestMapper = requestMapper;
    }

    @Operation(summary = "Create a user", description = "Creates a new user account, including its initial password. Request payload is an UpsertUserRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Username/email/phone already in use"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertUserRequest.class)
    @PostMapping
    public ResponseEntity<CommonResponseObject> create(@Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertUserRequest request = requestMapper.requireRequestObject(commonRequest, UpsertUserRequest.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(CommonResponseObject.success(
                userService.create(new UamUserServiceUseCase.CreateInput(request)).user()));
    }

    @Operation(summary = "Update a user", description = "Replaces the mutable fields of an existing user account. Request payload is an UpsertUserRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "409", description = "Username/email/phone already in use"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(UpsertUserRequest.class)
    @PutMapping("/{id}")
    public ResponseEntity<CommonResponseObject> update(@Parameter(description = "User id") @PathVariable String id,
                                        @Valid @RequestBody CommonRequestObject commonRequest) {
        UpsertUserRequest request = requestMapper.requireRequestObject(commonRequest, UpsertUserRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.update(new UamUserServiceUseCase.UpdateInput(id, request)).user()));
    }

    @Operation(summary = "Change a user's status", description = "Sets a user's account status (e.g. ACTIVE, SUSPENDED, DISABLED), which affects their ability to log in.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<CommonResponseObject> updateStatus(@Parameter(description = "User id") @PathVariable String id,
                                              @Parameter(description = "New account status") @RequestParam UamEnums.UserStatus status) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.updateStatus(new UamUserServiceUseCase.UpdateStatusInput(id, status)).user()));
    }

    @Operation(summary = "Set a user's password", description = "Administratively sets a user's password, optionally flagging it as temporary (forcing a change on next login). Request payload is a SetPasswordRequest.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password set"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @RequestObjectType(SetPasswordRequest.class)
    @PatchMapping("/{id}/password")
    public ResponseEntity<CommonResponseObject> setPassword(@Parameter(description = "User id") @PathVariable String id,
                                             @Valid @RequestBody CommonRequestObject commonRequest) {
        SetPasswordRequest request = requestMapper.requireRequestObject(commonRequest, SetPasswordRequest.class);
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.setPassword(new UamUserServiceUseCase.SetPasswordInput(
                        id, request.password(), Boolean.TRUE.equals(request.temporary()))).user()));
    }

    @Operation(summary = "Set whether TOTP MFA is required", description = "Toggles whether the user must complete a TOTP challenge on login.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "TOTP requirement updated"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PatchMapping("/{id}/totp-required")
    public ResponseEntity<CommonResponseObject> setTotpRequired(@Parameter(description = "User id") @PathVariable String id,
                                                 @Parameter(description = "Whether TOTP should be required on login") @RequestParam boolean required) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.setTotpRequired(new UamUserServiceUseCase.SetTotpRequiredInput(id, required)).user()));
    }

    @Operation(summary = "Look up several users by id",
            description = "Returns the minimal display details (id, user name, display name, email, phone) of up to 100 users in one call; ids that do not exist are omitted. Used by other services to show who a user id is.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users found (possibly fewer than requested)"),
            @ApiResponse(responseCode = "400", description = "More than 100 ids"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/batch")
    public ResponseEntity<CommonResponseObject> batch(
            @Parameter(description = "Comma separated user ids (at most 100)") @RequestParam(name = "ids") java.util.List<String> ids) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.findByIds(new UamUserServiceUseCase.FindByIdsInput(ids)).users().stream()
                        .map(UserSummaryResponse::from).toList()));
    }

    @Operation(summary = "Get a user by id", description = "Fetches a single user's account details.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    public ResponseEntity<CommonResponseObject> get(@Parameter(description = "User id") @PathVariable String id) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.get(new UamUserServiceUseCase.GetInput(id)).user()));
    }

    /** For sibling-service linkage (e.g. AdminAPI resolving a driver/vendor's UAM identity by
     *  phone during approval/onboarding) -- returns success with a null payload on a miss,
     *  not a 404/error, since "no account yet" is an expected, valid outcome for a caller. */
    @Operation(summary = "Find a user by phone number",
            description = "For sibling-service linkage (e.g. AdminAPI resolving a driver/vendor's UAM identity during approval/onboarding). Returns success with a null payload if no account exists for this phone -- that is an expected, valid outcome, not an error.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lookup completed (payload is null if no matching account exists)"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<CommonResponseObject> getByPhone(@Parameter(description = "Phone number to look up") @PathVariable String phone) {
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.findByPhone(new UamUserServiceUseCase.FindByPhoneInput(phone)).user().orElse(null)));
    }

    @Operation(summary = "List users", description = "Returns a page of user accounts.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of users returned"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    public ResponseEntity<CommonResponseObject> list(@Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
                               @Parameter(description = "Page size") @RequestParam(defaultValue = "50") int size,
                               @Parameter(description = "Filter by role/user-type code") @RequestParam(required = false) String roleCode,
                               @Parameter(description = "Filter by account status") @RequestParam(required = false) UamEnums.UserStatus status,
                               @Parameter(description = "Free-text search across username/email/phone/name (also fed by username/email query params)") @RequestParam(required = false) String search,
                               @Parameter(description = "Username filter (folded into free-text search)") @RequestParam(required = false) String username,
                               @Parameter(description = "Email filter (folded into free-text search)") @RequestParam(required = false) String email,
                               @Parameter(description = "Restrict to a tenant (plus global users)") @RequestParam(required = false) String tenantScope) {
        String effectiveSearch = combineSearchTerms(search, username, email);
        if (roleCode != null || status != null || effectiveSearch != null || tenantScope != null) {
            return ResponseEntity.ok(CommonResponseObject.success(
                    userService.search(new UamUserServiceUseCase.SearchInput(
                            page, size, roleCode, status, effectiveSearch, tenantScope)).users()));
        }
        return ResponseEntity.ok(CommonResponseObject.success(
                userService.list(new UamUserServiceUseCase.ListPageInput(page, size)).users()));
    }

    /** Folds the discrete username/email filters the frontend sends into the single free-text
     *  {@code search} param that {@link com.heuristiq.uam.users.repository.UamUserSearchCriteria}
     *  actually supports (it has no per-field username/email constraints). */
    private String combineSearchTerms(String search, String username, String email) {
        StringBuilder combined = new StringBuilder();
        if (search != null && !search.isBlank()) {
            combined.append(search.trim());
        }
        if (username != null && !username.isBlank()) {
            if (combined.length() > 0) combined.append(' ');
            combined.append(username.trim());
        }
        if (email != null && !email.isBlank()) {
            if (combined.length() > 0) combined.append(' ');
            combined.append(email.trim());
        }
        return combined.length() == 0 ? null : combined.toString();
    }

    @Operation(summary = "Delete a user", description = "Permanently removes a user account.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "User deleted"),
            @ApiResponse(responseCode = "404", description = "User not found"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<CommonResponseObject> delete(@Parameter(description = "User id") @PathVariable String id) {
        userService.delete(new UamUserServiceUseCase.DeleteInput(id));
        return ResponseEntity.noContent().build();
    }
}
