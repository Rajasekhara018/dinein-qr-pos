package com.heuristiq.uam.franchiseaccess;

import java.util.List;
import java.util.Set;

/** Request and response shapes of the franchise-access internal API (plain JSON inside the standard response envelope). */
public final class FranchiseAccessModels {

    private FranchiseAccessModels() {
    }

    /** An application as the franchise service shows it in mails; {@code found} is false (all else null) for an unknown code. */
    public record ApplicationResponse(boolean found, String code, String name, String landingPage, String baseUrl) {
        public static ApplicationResponse missing() {
            return new ApplicationResponse(false, null, null, null, null);
        }
    }

    /** The codes (upper-cased, in alphabetical order) of every active application; the franchise service makes each one's owner and agent roles. */
    public record ApplicationCodesResponse(List<String> codes) {
    }

    public record ExistsResponse(boolean exists) {
    }

    /** An account as the franchise service needs it; {@code found} is false for an unknown account. */
    public record UserResponse(boolean found, String userId, String displayName, String email, List<String> roles) {
        public static UserResponse missing() {
            return new UserResponse(false, null, null, null, List.of());
        }
    }

    /** The accounts of a batch lookup that exist and are active members of the organization; the others are left out. */
    public record UsersResponse(List<UserResponse> users) {
    }

    public record ActiveResponse(boolean active) {
    }

    /**
     * Whether the organization is linked to the application today (an ACTIVE subscription that has not passed its end date).
     * The names are for the message the franchise service shows when it is not; either is null when unknown.
     */
    public record SubscribedResponse(boolean subscribed, String organizationName, String applicationName) {
    }

    /** Whether an active member of the organization holds a privilege through the roles of that organization (or shared roles). */
    public record PrivilegeResponse(boolean granted) {
    }

    public record AddMemberRequest(String userId, String actor) {
    }

    public record SupportEmailResponse(String email) {
    }

    public record RoleCodesResponse(List<String> codes) {
    }

    public record InUseResponse(boolean inUse) {
    }

    public record EnsureRoleRequest(String roleCode, String name, String description, Set<String> privileges) {
    }

    public record AddRolesRequest(List<String> roleCodes) {
    }

    public record SubscribeRequest(String applicationCode, String actor) {
    }

    public record GrantRequest(String applicationCode, String actor, String note) {
    }

    public record CreateLoginRequest(String email, String name, String phone, List<String> roleCodes) {
    }

    public record CreateLoginResponse(String userId) {
    }

    /** The empty answer of the calls that only act. */
    public record EmptyResponse() {
    }
}
