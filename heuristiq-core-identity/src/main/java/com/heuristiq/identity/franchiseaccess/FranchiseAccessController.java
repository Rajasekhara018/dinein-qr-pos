package com.heuristiq.uam.franchiseaccess;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.ActiveResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.AddMemberRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.AddRolesRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.ApplicationCodesResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.CreateLoginRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.CreateLoginResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.EmptyResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.EnsureRoleRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.ExistsResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.GrantRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.InUseResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.PrivilegeResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.RoleCodesResponse;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.SubscribeRequest;
import com.heuristiq.uam.franchiseaccess.FranchiseAccessModels.SupportEmailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Service-to-service API for Rider_FranchiseService: reachable only with the shared {@code X-Rider-Service-Token}
 * (InternalServiceSecurityConfig), never with an end-user token, and not in the privilege catalogue. The organization is
 * named in the path, since a service has no organization of its own.
 */
@RestController
@RequestMapping(FranchiseAccessConstants.BASE_PATH)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@Tag(name = "Franchise access (internal)", description = "Accounts, roles, memberships, subscriptions and grants for the franchise service.")
public class FranchiseAccessController {

    private final FranchiseAccessService access;

    public FranchiseAccessController(FranchiseAccessService access) {
        this.access = access;
    }

    @Operation(summary = "The codes of every active application, so the franchise service can make each one's owner and agent roles")
    @GetMapping("/applications")
    public ResponseEntity<CommonResponseObject> applicationCodes() {
        return ok(new ApplicationCodesResponse(access.applicationCodes()));
    }

    @Operation(summary = "Whether an application with this code is registered")
    @GetMapping("/applications/{code}/exists")
    public ResponseEntity<CommonResponseObject> applicationExists(@PathVariable String code) {
        return ok(new ExistsResponse(access.applicationExists(code)));
    }

    @Operation(summary = "An application by code: name, landing page and base URL; found is false for an unknown code")
    @GetMapping("/applications/{code}")
    public ResponseEntity<CommonResponseObject> application(@PathVariable String code) {
        return ok(access.application(code));
    }

    @Operation(summary = "The account with this email or username, only when it is an active member of the given organization")
    @GetMapping("/users/by-email")
    public ResponseEntity<CommonResponseObject> userByEmail(@RequestParam String tenantId, @RequestParam String email) {
        return ok(access.userByEmail(tenantId, email));
    }

    @Operation(summary = "An account by id; found is false for an unknown id or one that is not an active member of the given organization")
    @GetMapping("/users/{userId}")
    public ResponseEntity<CommonResponseObject> user(@PathVariable String userId, @RequestParam String tenantId) {
        return ok(access.userById(tenantId, userId));
    }

    @Operation(summary = "Several accounts by id (at most 100); ids that are unknown or not active members of the organization are left out")
    @GetMapping("/users")
    public ResponseEntity<CommonResponseObject> users(@RequestParam String tenantId, @RequestParam(required = false) java.util.List<String> ids) {
        return ok(access.usersByIds(tenantId, ids));
    }

    @Operation(summary = "Whether the account is an active member of the organization")
    @GetMapping("/tenants/{tenantId}/members/{userId}")
    public ResponseEntity<CommonResponseObject> member(@PathVariable String tenantId, @PathVariable String userId) {
        return ok(new ActiveResponse(access.isActiveMember(tenantId, userId)));
    }

    @Operation(summary = "Makes an existing account a member of the organization")
    @PostMapping("/tenants/{tenantId}/members")
    public ResponseEntity<CommonResponseObject> addMember(@PathVariable String tenantId, @RequestBody AddMemberRequest request) {
        access.addMember(tenantId, request.userId(), request.actor());
        return ok(new EmptyResponse());
    }

    @Operation(summary = "The support email of the organization, null when it has none")
    @GetMapping("/tenants/{tenantId}/support-email")
    public ResponseEntity<CommonResponseObject> supportEmail(@PathVariable String tenantId) {
        return ok(new SupportEmailResponse(access.supportEmail(tenantId).orElse(null)));
    }

    @Operation(summary = "Every role code the organization may use, upper-cased")
    @GetMapping("/tenants/{tenantId}/role-codes")
    public ResponseEntity<CommonResponseObject> roleCodes(@PathVariable String tenantId) {
        return ok(new RoleCodesResponse(access.roleCodes(tenantId).stream().sorted().toList()));
    }

    @Operation(summary = "Whether an account already uses this email")
    @GetMapping("/email-in-use")
    public ResponseEntity<CommonResponseObject> emailInUse(@RequestParam String email) {
        return ok(new InUseResponse(access.emailInUse(email)));
    }

    @Operation(summary = "Creates a fixed franchise role when missing and adds any privilege an existing one lacks (never removes one)")
    @PostMapping("/roles/ensure")
    public ResponseEntity<CommonResponseObject> ensureRole(@RequestBody EnsureRoleRequest request) {
        access.ensureRole(request.roleCode(), request.name(), request.description(), request.privileges());
        return ok(new EmptyResponse());
    }

    @Operation(summary = "Gives the account these roles as well as its own")
    @PostMapping("/users/{userId}/roles")
    public ResponseEntity<CommonResponseObject> addRoles(@PathVariable String userId, @RequestBody AddRolesRequest request) {
        access.addRoles(userId, request.roleCodes());
        return ok(new EmptyResponse());
    }

    @Operation(summary = "Links the organization to the application when it is not linked yet")
    @PostMapping("/tenants/{tenantId}/subscriptions")
    public ResponseEntity<CommonResponseObject> subscribe(@PathVariable String tenantId, @RequestBody SubscribeRequest request) {
        access.subscribe(tenantId, request.applicationCode(), request.actor());
        return ok(new EmptyResponse());
    }

    @Operation(summary = "The applications the organization uses today (its active subscriptions); read only")
    @GetMapping("/tenants/{tenantId}/subscriptions")
    public ResponseEntity<CommonResponseObject> subscriptions(@PathVariable String tenantId) {
        return ok(new ApplicationCodesResponse(access.subscribedApplicationCodes(tenantId)));
    }

    @Operation(summary = "Whether the organization uses the application today (an active subscription); read only, never subscribes")
    @GetMapping("/tenants/{tenantId}/subscriptions/{applicationCode}")
    public ResponseEntity<CommonResponseObject> subscription(@PathVariable String tenantId, @PathVariable String applicationCode) {
        return ok(access.subscription(tenantId, applicationCode));
    }

    @Operation(summary = "Whether an active member of the organization holds a privilege through the roles of that organization")
    @GetMapping("/tenants/{tenantId}/users/{userId}/privileges/{privilege}")
    public ResponseEntity<CommonResponseObject> privilege(@PathVariable String tenantId, @PathVariable String userId, @PathVariable String privilege) {
        return ok(new PrivilegeResponse(access.holdsPrivilege(tenantId, userId, privilege)));
    }

    @Operation(summary = "Lets the account sign in to the application; granting twice is harmless")
    @PostMapping("/users/{userId}/application-grants")
    public ResponseEntity<CommonResponseObject> grant(@PathVariable String userId, @RequestBody GrantRequest request) {
        access.grant(userId, request.applicationCode(), request.actor(), request.note());
        return ok(new EmptyResponse());
    }

    @Operation(summary = "Creates a staff login in the organization that signs in with an emailed one-time code")
    @PostMapping("/tenants/{tenantId}/logins")
    public ResponseEntity<CommonResponseObject> createLogin(@PathVariable String tenantId, @RequestBody CreateLoginRequest request) {
        return ok(new CreateLoginResponse(access.createLogin(tenantId, request.email(), request.name(), request.phone(), request.roleCodes())));
    }

    @Operation(summary = "Removes a login created for the franchise service that could not be set up; an unknown login is already gone")
    @DeleteMapping("/tenants/{tenantId}/logins/{userId}")
    public ResponseEntity<CommonResponseObject> deleteLogin(@PathVariable String tenantId, @PathVariable String userId) {
        access.deleteLogin(tenantId, userId);
        return ok(new EmptyResponse());
    }

    private static ResponseEntity<CommonResponseObject> ok(Object body) {
        return ResponseEntity.ok(CommonResponseObject.success(body));
    }
}
