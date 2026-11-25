package com.heuristiq.identity.users;

import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.helper.RUpload;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Hand-authored builder for the "provision a brand-new {@link UamUser}" shape that every user
 * creation entry point re-implements with its own copy-pasted defaults: native registration
 * ({@code UserRegistrationService}), admin-created users ({@code UamUserService}), Google sign-in
 * ({@code UamGoogleAuthService}), Keycloak/OIDC external sign-in ({@code ExternalIdentityResolver}),
 * and the bootstrap super-admin seed ({@code UamBootstrapRunner}).
 *
 * <p>Each of those call sites supplies a different subset of identity fields (a Google login has a
 * profile photo and no phone number, native registration has a phone number and no photo, admin
 * creation may pin an explicit status, etc.) but they all share the same three provisioning rules
 * that used to be duplicated verbatim at every site:
 * <ul>
 *     <li>a newly-provisioned account is always {@code active}</li>
 *     <li>its status defaults to {@link UamEnums.UserStatus#ACTIVE} unless the caller pins one
 *     explicitly (only the admin-created-user flow ever does)</li>
 *     <li>it is stamped with a brand-new {@link AuditInfo}, timestamped once at {@link #build()}</li>
 * </ul>
 *
 * <p>Field assembly itself is still delegated to Lombok's generated {@code UamUser.UamUserBuilder} --
 * this class only owns the provisioning defaults above, it is not a replacement for Lombok's builder.
 */
public final class NewUamUserBuilder {

    private final UamUser.UamUserBuilder delegate = UamUser.builder();
    private UamEnums.UserStatus status;

    private NewUamUserBuilder() {
    }

    public static NewUamUserBuilder newUser() {
        return new NewUamUserBuilder();
    }

    public NewUamUserBuilder userName(String userName) {
        delegate.userName(userName);
        return this;
    }

    public NewUamUserBuilder email(String email) {
        delegate.email(email);
        return this;
    }

    public NewUamUserBuilder phoneNumber(RPhoneNumber phoneNumber) {
        delegate.phoneNumber(phoneNumber);
        return this;
    }

    public NewUamUserBuilder fullName(RName fullName) {
        delegate.fullName(fullName);
        return this;
    }

    public NewUamUserBuilder profileImage(RUpload profileImage) {
        delegate.profileImage(profileImage);
        return this;
    }

    /**
     * Optional. Leave unset (or pass {@code null}) to fall back to {@code ACTIVE}, which is what
     * every self-service and identity-provider signup wants; only the admin-created-user flow ever
     * pins an explicit status up front.
     */
    public NewUamUserBuilder status(UamEnums.UserStatus status) {
        this.status = status;
        return this;
    }

    /** The organization the account belongs to; leave unset only for accounts that are not created inside one. */
    public NewUamUserBuilder tenantId(String tenantId) {
        delegate.tenantId(tenantId);
        return this;
    }

    public NewUamUserBuilder roles(List<String> roles) {
        delegate.roles(roles);
        return this;
    }

    public UamUser build() {
        LocalDateTime now = LocalDateTime.now();
        delegate.status(status != null ? status : UamEnums.UserStatus.ACTIVE);
        delegate.active(true);
        delegate.audit(AuditInfo.builder().createdAt(now).updatedAt(now).build());
        return delegate.build();
    }
}
