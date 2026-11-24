package com.heuristiq.uam.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.helper.IanaTimeZone;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.util.UserTypeResolver;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record LoginResponse(
        String token,
        String accessToken,
        @JsonProperty("access_token") String accessTokenSnake,
        String refreshToken,
        String userId,
        @JsonProperty("driver_id") String driverIdSnake,
        String driverId,
        String username,
        String email,
        String fullName,
        UamEnums.UserType userType,
        String role,
        List<String> roles,
        @Schema(description = "The signed-in user's saved IANA timezone (for example Asia/Kolkata), or null when none is saved or the stored value is not a valid zone. Display hint only.",
                nullable = true, example = "Asia/Kolkata") String timezone,
        @Schema(description = "IANA timezone of the session organization (the token's tenant), or null when unset or invalid. Display hint only.",
                nullable = true, example = "Asia/Kolkata") String organizationTimeZone,
        @Schema(description = "Id of the session organization (the token's tenant), or null when the session has none or it cannot be resolved. Informational; the token's tenantId claim stays authoritative.",
                nullable = true, example = "default") String organizationId,
        @Schema(description = "Code of the session organization, or null when there is none. Informational.",
                nullable = true, example = "DEFAULT") String organizationCode,
        @Schema(description = "Display name of the session organization, or null when there is none. Informational.",
                nullable = true, example = "Default Organization") String organizationName,
        @Schema(description = "True while the account's password is a temporary one (issued by an administrator or on activation): the client must make the person choose their own password before anything else. Cleared when they change it.",
                example = "false") boolean forcePasswordChange
) implements LoginResult {

    /** The response as it was before the force-password-change flag existed; the flag is false. */
    public LoginResponse(String token, String accessToken, String accessTokenSnake, String refreshToken, String userId,
                         String driverIdSnake, String driverId, String username, String email, String fullName,
                         UamEnums.UserType userType, String role, List<String> roles, String timezone,
                         String organizationTimeZone, String organizationId, String organizationCode,
                         String organizationName) {
        this(token, accessToken, accessTokenSnake, refreshToken, userId, driverIdSnake, driverId, username, email,
                fullName, userType, role, roles, timezone, organizationTimeZone, organizationId, organizationCode,
                organizationName, false);
    }

    /** A copy of this response with the force-password-change flag set as given. */
    public LoginResponse withForcePasswordChange(boolean force) {
        return new LoginResponse(token, accessToken, accessTokenSnake, refreshToken, userId, driverIdSnake, driverId,
                username, email, fullName, userType, role, roles, timezone, organizationTimeZone, organizationId,
                organizationCode, organizationName, force);
    }

    /** The response as it was before the organization identity fields existed; those three are null. */
    public LoginResponse(String token, String accessToken, String accessTokenSnake, String refreshToken, String userId,
                         String driverIdSnake, String driverId, String username, String email, String fullName,
                         UamEnums.UserType userType, String role, List<String> roles, String timezone,
                         String organizationTimeZone) {
        this(token, accessToken, accessTokenSnake, refreshToken, userId, driverIdSnake, driverId, username, email,
                fullName, userType, role, roles, timezone, organizationTimeZone, null, null, null);
    }

    /** The response as it was before the timezone fields existed; both new fields are null. */
    public LoginResponse(String token, String accessToken, String accessTokenSnake, String refreshToken, String userId,
                         String driverIdSnake, String driverId, String username, String email, String fullName,
                         UamEnums.UserType userType, String role, List<String> roles) {
        this(token, accessToken, accessTokenSnake, refreshToken, userId, driverIdSnake, driverId, username, email,
                fullName, userType, role, roles, null, null, null, null, null);
    }

    /**
     * The one place a login/refresh response is built (password, OTP and Google login all go
     * through LoginSessionService). token / accessToken / access_token are the same JWT under the
     * names different clients read; driverId / driver_id are only set for DRIVER users.
     */
    public static LoginResponse of(UamUser user, String token, String refreshToken) {
        return of(user, token, refreshToken, (String) null);
    }

    /**
     * Same as {@link #of(UamUser, String, String)} plus the zone of the session organization (taken from the verified
     * tenant, never from the request). Stored zones that are not valid IANA ids are returned as null.
     */
    public static LoginResponse of(UamUser user, String token, String refreshToken, String organizationTimeZone) {
        return build(user, token, refreshToken, organizationTimeZone, null, null, null);
    }

    /**
     * Same as {@link #of(UamUser, String, String)} plus the session organization's identity and zone, all taken from the
     * verified tenant the token is issued for (never from the request). A null organization leaves the four fields null.
     */
    public static LoginResponse of(UamUser user, String token, String refreshToken, Tenant organization) {
        return of(user, token, refreshToken, organization, user.getRoles());
    }

    /**
     * Same as {@link #of(UamUser, String, String, Tenant)} showing {@code sessionRoles}, the roles the session's token
     * carries (a franchise role without an active franchise is not among them), instead of every role of the account.
     * {@code role}, {@code roles}, {@code userType} and {@code driverId} all follow them.
     */
    public static LoginResponse of(UamUser user, String token, String refreshToken, Tenant organization, List<String> sessionRoles) {
        if (organization == null) {
            return build(user, sessionRoles, token, refreshToken, null, null, null, null);
        }
        return build(user, sessionRoles, token, refreshToken, organization.getTimeZone(), organization.getId(),
                organization.getCode(), organization.getName());
    }

    private static LoginResponse build(UamUser user, String token, String refreshToken, String organizationTimeZone,
                                       String organizationId, String organizationCode, String organizationName) {
        return build(user, user.getRoles(), token, refreshToken, organizationTimeZone, organizationId, organizationCode,
                organizationName);
    }

    private static LoginResponse build(UamUser user, List<String> sessionRoles, String token, String refreshToken,
                                       String organizationTimeZone, String organizationId, String organizationCode,
                                       String organizationName) {
        List<String> roles = sessionRoles == null ? List.of() : sessionRoles;
        String role = roles.stream().findFirst().orElse("USER");
        UamEnums.UserType userType = UserTypeResolver.resolve(roles);
        String driverId = userType == UamEnums.UserType.DRIVER ? user.getId() : null;
        return new LoginResponse(
                token,
                token,
                token,
                refreshToken,
                user.getId(),
                driverId,
                driverId,
                user.getUserName(),
                user.getEmail(),
                displayName(user),
                userType,
                role,
                roles,
                IanaTimeZone.validOrNull(user.getTimezone()),
                IanaTimeZone.validOrNull(organizationTimeZone),
                blankToNull(organizationId),
                blankToNull(organizationCode),
                blankToNull(organizationName));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** RName joins first/middle/last with spaces, leaving stray spaces when a part is empty. */
    private static String displayName(UamUser user) {
        if (user.getFullName() == null || user.getFullName().getFullName() == null) {
            return null;
        }
        String name = user.getFullName().getFullName().trim().replaceAll("\\s+", " ");
        return name.isEmpty() ? null : name;
    }
}
