package com.heuristiq.uam.google;

import org.springframework.http.HttpHeaders;
import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.auth.ClientIpResolver;
import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.auth.MfaLoginGate;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.usercredentials.UserCredential;
import com.heuristiq.uam.auth.SelfServiceRoleGrant;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RName;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.users.NewUamUserBuilder;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.usercredentials.UserCredentialRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UamGoogleAuthService implements UamGoogleAuthServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyRegistration policyRegistration;
    private final UamUserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final GoogleIdentityTokenVerifier tokenVerifier;
    private final LoginSessionService loginSessionService;
    private final MfaLoginGate mfaLoginGate;
    private final IdentityProviderSyncService identityProviderSyncService;
    private final HttpServletRequest httpRequest;

    public UamGoogleAuthService(UamUserRepository userRepository,
                                UserCredentialRepository userCredentialRepository,
                                GoogleIdentityTokenVerifier tokenVerifier,
                                LoginSessionService loginSessionService,
                                MfaLoginGate mfaLoginGate,
                                IdentityProviderSyncService identityProviderSyncService,
                                HttpServletRequest httpRequest) {
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.tokenVerifier = tokenVerifier;
        this.loginSessionService = loginSessionService;
        this.mfaLoginGate = mfaLoginGate;
        this.identityProviderSyncService = identityProviderSyncService;
        this.httpRequest = httpRequest;
    }

    public LoginOutput login(LoginInput input) {
        return new LoginOutput(login(new LoginWithTypeInput(input.idToken(), UamEnums.UserType.CUSTOMER)).response());
    }

    public LoginWithTypeOutput login(LoginWithTypeInput input) {
        String idToken = input.idToken();
        UamEnums.UserType userType = input.userType();
        GoogleIdentity identity = tokenVerifier.verify(idToken);

        Optional<UserCredential> linkedCredential =
                userCredentialRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, identity.firebaseUid());

        UamUser user;
        if (linkedCredential.isPresent()) {
            user = userRepository.findById(linkedCredential.get().getUserId())
                    .orElseThrow(() -> new GoogleAuthException(HttpStatus.NOT_FOUND, "Linked user not found."));
        } else {
            user = userRepository.findByEmailIgnoreCase(identity.email())
                    .orElseGet(() -> {
                        requireExistingAccountWhenApplicationNamed();
                        // The type comes from the request body: never create a staff account from it.
                        com.heuristiq.uam.auth.PublicAccountTypes.requireSignInProvisionable(userType);
                        return createGoogleUser(identity, userType);
                    });
            userCredentialRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)
                    .ifPresent(existing -> {
                        throw new GoogleAuthException(HttpStatus.CONFLICT,
                                "This email is already linked to another Google account.");
                    });
            linkGoogleCredential(user.getId(), identity.firebaseUid());
        }

        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new GoogleAuthException(HttpStatus.FORBIDDEN, "User account is not active.");
        }

        user.setEmail(identity.email());
        if (StringUtils.hasText(identity.name())) {
            user.setFullName(new RName(identity.name()));
        }
        if (StringUtils.hasText(identity.photoUrl())) {
            RUpload image = user.getProfileImage() == null ? new RUpload() : user.getProfileImage();
            image.setLocation(identity.photoUrl());
            user.setProfileImage(image);
        }
        AuditInfo audit = user.getAudit() == null
                ? AuditInfo.builder().createdAt(LocalDateTime.now()).build()
                : user.getAudit();
        audit.setUpdatedAt(LocalDateTime.now());
        user.setAudit(audit);

        // The Google token is verified: a back-office account is refused by a customer app before anything is saved or granted.
        com.heuristiq.uam.auth.CustomerSignInGate.requireCustomerAppAccount(user, userType);

        // Users with MFA enrolled need their authenticator code too; checked before the profile update is saved.
        Optional<UserMfa> mfa = mfaLoginGate.requiredFor(user);
        if (mfa.isPresent() && !StringUtils.hasText(input.totpCode())) {
            return new LoginWithTypeOutput(MfaChallengeRequiredResponse.required(mfa.get().getMfaType()));
        }
        if (mfa.isPresent()) {
            mfaLoginGate.verifyOrReject(user, mfa.get(), input.totpCode());
        }
        // A driver signing in to the customer app with Google gets the CUSTOMER role on the same account (SelfServiceRoleGrant).
        if (SelfServiceRoleGrant.grantIfMissing(user, userType)) {
            log.info("uam.auth.self-service-role-granted user={} role={}", user.getId(), userType.name());
        }

        UamUser saved = userRepository.save(user);
        identityProviderSyncService.syncUser(saved);
        log.info("UAM user {} logged in with Google", saved.getId());

        // Same session + device + rotating refresh token + JWT as password and OTP login, so Google
        // users can refresh instead of being logged out when the 1-hour access token expires.
        return new LoginWithTypeOutput(loginSessionService.issue(
                saved, clientIp(), httpRequest.getHeader(HttpHeaders.USER_AGENT), mfa.isPresent()));
    }

    private String clientIp() {
        return ClientIpResolver.resolve(httpRequest);
    }

    /**
     * An application that names itself ({@value AuthConstants#APPLICATION_CODE_HEADER}) signs existing members in; its members
     * register with a phone number first. A Google email with no account is therefore refused with a clear 403 and no login is
     * created for it: a bare account has no phone number, which the stored user requires, so creating one only ended in an
     * unexplained server error. Callers that name no application keep the old behaviour (an account is created).
     */
    private void requireExistingAccountWhenApplicationNamed() {
        if (StringUtils.hasText(httpRequest.getHeader(AuthConstants.APPLICATION_CODE_HEADER))) {
            throw new GoogleAuthException(HttpStatus.FORBIDDEN, AuthConstants.GOOGLE_ACCOUNT_NOT_REGISTERED_MESSAGE);
        }
    }

    private UamUser createGoogleUser(GoogleIdentity identity, UamEnums.UserType userType) {
        RUpload profileImage = null;
        if (StringUtils.hasText(identity.photoUrl())) {
            profileImage = new RUpload();
            profileImage.setLocation(identity.photoUrl());
        }
        UamUser user = NewUamUserBuilder.newUser()
                .status(policyRegistration == null ? UamEnums.UserStatus.ACTIVE : policyRegistration.initialStatus())
                .userName(identity.email())
                .email(identity.email())
                .fullName(StringUtils.hasText(identity.name()) ? new RName(identity.name()) : null)
                .profileImage(profileImage)
                .roles(List.of(userType.name()))
                .build();
        return userRepository.save(user);
    }

    private void linkGoogleCredential(String userId, String firebaseUid) {
        LocalDateTime now = LocalDateTime.now();
        UserCredential credential = userCredentialRepository.findByUserIdAndProvider(userId, AuthProvider.GOOGLE)
                .orElseGet(() -> UserCredential.builder()
                        .userId(userId)
                        .provider(AuthProvider.GOOGLE)
                        .createdAt(now)
                        .build());
        credential.setProviderUserId(firebaseUid);
        credential.setVerified(true);
        credential.setEmailVerified(true);
        credential.setActive(true);
        credential.setLastAuthenticatedAt(now);
        credential.setUpdatedAt(now);
        userCredentialRepository.save(credential);
    }
}
