package com.heuristiq.uam.auth;

import org.springframework.http.HttpHeaders;
import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.refreshToken.RefreshTokenFailures;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.dto.LoginRequest;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.lockout.LoginAttemptServiceUseCase;
import com.heuristiq.uam.lockout.UserLoginSecurityServiceUseCase;
import com.heuristiq.uam.metrics.Outcome;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.mfa.UserMfaServiceUseCase;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Decides WHETHER a caller may log in or refresh a session: credentials, account lockout, MFA
 * gating, account status, and the audit trail of that decision. Once a login/refresh is
 * authorized, {@link LoginSessionService} takes over -- device fingerprinting, session creation,
 * refresh-token issuance/rotation, and JWT minting live there, not here.
 */
@Service
public class AuthService implements AuthServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    private final UamUserRepository userRepository;
    private final UserCredentialServiceUseCase userCredentialService;
    private final LoginAttemptServiceUseCase loginAttemptService;
    private final AuthAuditService authAuditService;
    private final UserMfaServiceUseCase userMfaService;
    private final UserLoginSecurityServiceUseCase userLoginSecurityService;
    private final AuditLogServiceUseCase auditLogService;
    private final LoginSessionService loginSessionService;
    private final HttpServletRequest httpRequest;
    private final UamMetrics metrics;

    public AuthService(UamUserRepository userRepository,
                       UserCredentialServiceUseCase userCredentialService,
                       LoginAttemptServiceUseCase loginAttemptService,
                       AuthAuditService authAuditService,
                       UserMfaServiceUseCase userMfaService,
                       UserLoginSecurityServiceUseCase userLoginSecurityService,
                       AuditLogServiceUseCase auditLogService,
                       LoginSessionService loginSessionService,
                       HttpServletRequest httpRequest,
                       UamMetrics metrics) {
        this.metrics = metrics;
        this.userRepository = userRepository;
        this.userCredentialService = userCredentialService;
        this.loginAttemptService = loginAttemptService;
        this.authAuditService = authAuditService;
        this.userMfaService = userMfaService;
        this.userLoginSecurityService = userLoginSecurityService;
        this.auditLogService = auditLogService;
        this.loginSessionService = loginSessionService;
        this.httpRequest = httpRequest;
    }

    public LoginOutput login(LoginInput input) {
        return metrics.recordLogin(() -> doLogin(input),
                out -> out.result() instanceof MfaChallengeRequiredResponse ? Outcome.MFA_REQUIRED : Outcome.SUCCESS);
    }

    private LoginOutput doLogin(LoginInput input) {
        LoginRequest request = input.request();
        if (loginAttemptService.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(request.username())).locked()) {
            authAuditService.loginBlocked(request.username());
            throw new UamException(AuthConstants.LOGIN_LOCKED_MESSAGE);
        }
        UamUser user = resolveUser(request.username())
                .orElseThrow(() -> rejected(request.username(), AuthConstants.INVALID_CREDENTIALS_MESSAGE));
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw rejected(user, UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        }
        if (!userCredentialService.verifyPassword(
                new UserCredentialServiceUseCase.VerifyPasswordInput(user.getId(), request.password())).verified()) {
            throw rejected(user, AuthConstants.INVALID_CREDENTIALS_MESSAGE);
        }
        // The password is right: a customer app still refuses a back-office account (before MFA or a session).
        CustomerSignInGate.requireCustomerAppAccount(user, request.userType());

        List<UserMfa> enabledMfa = userMfaService.listEnabledByUser(
                new UserMfaServiceUseCase.ListEnabledByUserInput(user.getId())).mfas();
        List<UserMfa> allowedMfa = policyEnforcer == null ? enabledMfa : enabledMfa.stream()
                .filter(method -> policyEnforcer.methodAllowed(user, method.getMfaType())).toList();
        UserMfa mfa = MfaLoginGate.selectPrimary(allowedMfa);
        if (!enabledMfa.isEmpty() && mfa == null)
            throw new UamException("The enrolled MFA method is not allowed by the security policy");

        if (mfa != null) {
            if (!StringUtils.hasText(request.totpCode())) {
                return new LoginOutput(MfaChallengeRequiredResponse.required(mfa.getMfaType()));
            }
            if (!userMfaService.verifyCode(new UserMfaServiceUseCase.VerifyCodeInput(mfa, request.totpCode())).verified()) {
                throw rejected(user, AuthConstants.INVALID_AUTHENTICATOR_CODE_MESSAGE);
            }
        }

        LoginResponse response = finishLogin(user, mfa != null);
        loginAttemptService.recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput(request.username()));
        return new LoginOutput(response);
    }

    public LoginViaExternalProviderOutput loginViaExternalProvider(LoginViaExternalProviderInput input) {
        UamUser user = input.user();
        if (loginAttemptService.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(user.getUserName())).locked())
            throw new UamException(AuthConstants.LOGIN_LOCKED_MESSAGE);
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE)
            throw new UamException(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        List<UserMfa> enrolled = userMfaService.listEnabledByUser(new UserMfaServiceUseCase.ListEnabledByUserInput(user.getId())).mfas();
        List<UserMfa> allowed = policyEnforcer == null ? enrolled : enrolled.stream()
                .filter(method -> policyEnforcer.methodAllowed(user, method.getMfaType())).toList();
        UserMfa mfa = MfaLoginGate.selectPrimary(allowed);
        if (!enrolled.isEmpty() && mfa == null) throw new UamException("No enrolled MFA method is allowed by the security policy");
        if (mfa != null) {
            if (!StringUtils.hasText(input.totpCode()))
                throw new UamException("An authenticator code is required", org.springframework.http.HttpStatus.FORBIDDEN, "MFA_CHALLENGE_REQUIRED");
            if (!userMfaService.verifyCode(new UserMfaServiceUseCase.VerifyCodeInput(mfa, input.totpCode())).verified())
                throw rejected(user, AuthConstants.INVALID_AUTHENTICATOR_CODE_MESSAGE);
        }
        // A provider login is not proof of a locally verified second factor.
        LoginResponse response = finishLogin(user, mfa != null);
        loginAttemptService.recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput(user.getUserName()));
        return new LoginViaExternalProviderOutput(response);
    }

    @Override
    public LoginWithBiometricOutput loginWithBiometric(LoginWithBiometricInput input) {
        UamUser user = input.user();
        if (loginAttemptService.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(user.getUserName())).locked()) {
            authAuditService.loginBlocked(user.getUserName());
            throw new UamException(AuthConstants.LOGIN_LOCKED_MESSAGE);
        }
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            authAuditService.loginFailed(user.getUserName(), UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
            throw new UamException(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        }
        // The device key is unlocked only by the person's face or finger: it is the second factor.
        LoginResponse response = finishLogin(user, true, AuthConstants.AUTH_METHOD_BIOMETRIC);
        loginAttemptService.recordSuccess(new LoginAttemptServiceUseCase.RecordSuccessInput(user.getUserName()));
        return new LoginWithBiometricOutput(response);
    }

    private LoginResponse finishLogin(UamUser user, boolean mfaVerified) {
        return finishLogin(user, mfaVerified, null);
    }

    private LoginResponse finishLogin(UamUser user, boolean mfaVerified, String authMethod) {
        String ip = clientIp();
        String userAgent = userAgentHeader();
        LoginResponse response = authMethod == null ? loginSessionService.issue(user, ip, userAgent, mfaVerified)
                : loginSessionService.issue(user, ip, userAgent, mfaVerified, authMethod);

        userLoginSecurityService.recordSuccess(new UserLoginSecurityServiceUseCase.RecordSuccessInput(user.getId(), ip));
        authAuditService.loginSucceeded(user);
        recordAuditLog(user, UamEnums.AuditAction.LOGIN, UamEnums.AuditStatus.SUCCESS, "Login succeeded", ip, userAgent);

        return response;
    }

    public RefreshOutput refresh(RefreshInput input) {
        return metrics.recordRefresh(() -> doRefresh(input));
    }

    private RefreshOutput doRefresh(RefreshInput input) {
        String rawRefreshToken = input.rawRefreshToken();
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new UamException("Refresh token is required");
        }
        LoginSessionService.ResolvedRefresh resolved = loginSessionService.resolveForRefresh(rawRefreshToken)
                .orElseThrow(RefreshTokenFailures::invalid);
        if (resolved.user().getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        }
        return new RefreshOutput(loginSessionService.rotate(resolved));
    }

    private void recordAuditLog(UamUser user, UamEnums.AuditAction action, UamEnums.AuditStatus status,
                                String remarks, String ip, String userAgent) {
        try {
            auditLogService.record(new AuditLogServiceUseCase.RecordInput(AuditLog.builder()
                    .tenantId(user.getTenantId())
                    .userId(user.getId())
                    .username(user.getUserName())
                    .entityType(AuthConstants.AUTH_SESSION_ENTITY)
                    .entityId(user.getId())
                    .action(action)
                    .status(status)
                    .remarks(remarks)
                    .ipAddress(ip)
                    .userAgent(userAgent)
                    .correlationId(MDC.get(UamCommonConstants.CORRELATION_ID_MDC_KEY))
                    .build()));
        } catch (RuntimeException ex) {
            // Best-effort: a broken audit write must never fail a login.
        }
    }

    // Login identifier can be a username, email, or mobile number — try each in turn.
    private Optional<UamUser> resolveUser(String identifier) {
        return userRepository.findByUserNameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .or(() -> userRepository.findByPhone(identifier));
    }

    private String clientIp() {
        return ClientIpResolver.resolve(httpRequest);
    }

    private String userAgentHeader() {
        return httpRequest.getHeader(HttpHeaders.USER_AGENT);
    }

    // User couldn't even be resolved from the identifier -- nothing to attribute the failure to.
    private UamException rejected(String username, String message) {
        loginAttemptService.recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput(username));
        authAuditService.loginFailed(username, message);
        return new UamException(message);
    }

    // Failure on a resolved account (bad password / inactive / bad TOTP code) -- also update the
    // real UserLoginSecurity record and audit trail so the self-service screens reflect it.
    private UamException rejected(UamUser user, String message) {
        loginAttemptService.recordFailure(new LoginAttemptServiceUseCase.RecordFailureInput(user.getUserName()));
        authAuditService.loginFailed(user.getUserName(), message);
        boolean locked = loginAttemptService.isLocked(new LoginAttemptServiceUseCase.IsLockedInput(user.getUserName())).locked();
        String ip = clientIp();
        userLoginSecurityService.recordFailure(new UserLoginSecurityServiceUseCase.RecordFailureInput(user.getId(), ip, locked));
        recordAuditLog(user, UamEnums.AuditAction.LOGIN, UamEnums.AuditStatus.FAILURE, message, ip, userAgentHeader());
        return new UamException(message);
    }
}
