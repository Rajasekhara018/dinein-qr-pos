package com.heuristiq.identity.identity;

import com.heuristiq.identity.common.UamCommonConstants;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.users.NewUamUserBuilder;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Bridges an externally-authenticated identity (Keycloak/OIDC) onto a local {@link UamUser} --
 * finds the existing user by email, or provisions one on first login. Backend-agnostic: goes
 * through {@link UamUserRepository} only, same as every other identity flow in this service.
 */
@Component
public class ExternalIdentityResolver {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyRegistration policyRegistration;

    private final UamUserRepository userRepository;

    public ExternalIdentityResolver(UamUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UamUser resolveOrCreate(ExternalUserInfo info, UamEnums.UserType defaultUserType) {
        if (!StringUtils.hasText(info.email())) {
            throw new UamException("Identity provider did not return an email claim.");
        }
        UamUser user = userRepository.findByEmailIgnoreCase(info.email())
                .orElseGet(() -> {
                    UamEnums.UserType type = defaultUserType != null ? defaultUserType : UamEnums.UserType.OPS;
                    // The requested type comes from the request body: never create a staff account from it.
                    com.heuristiq.identity.auth.PublicAccountTypes.requireSignInProvisionable(type);
                    return createUser(info, type);
                });

        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        }
        return user;
    }

    private UamUser createUser(ExternalUserInfo info, UamEnums.UserType userType) {
        String displayName = StringUtils.hasText(info.name()) ? info.name() : info.preferredUsername();
        UamUser user = NewUamUserBuilder.newUser()
                .status(policyRegistration == null ? UamEnums.UserStatus.ACTIVE : policyRegistration.initialStatus())
                .userName(info.email())
                .email(info.email())
                .fullName(StringUtils.hasText(displayName) ? new RName(displayName) : null)
                .roles(List.of(userType.name()))
                .build();
        return userRepository.save(user);
    }
}
