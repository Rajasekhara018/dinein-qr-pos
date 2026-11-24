package com.heuristiq.uam.auth;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.UserAgentParser.ParsedUserAgent;
import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.organization.OrgWideAccess;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.membership.TenancyConstants;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.security.SessionAccess;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.uam.refreshToken.RefreshTokenFailures;
import com.heuristiq.uam.refreshToken.RefreshTokenHashing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns everything that happens once a login/refresh is already authorized: device
 * fingerprinting, session creation, refresh-token issuance/rotation, and JWT minting. {@link
 * AuthService} decides WHETHER a caller may log in or refresh (credentials, lockout, MFA,
 * account status); this class turns that decision into a session + tokens, so AuthService
 * doesn't have to know about device/session/token mechanics.
 */
@Service
public class LoginSessionService {
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.beans.factory.ObjectProvider<com.heuristiq.uam.securitypolicy.SecurityPolicyEnrollmentService> enrollment;
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.EffectiveSecurityPolicyResolver policyResolver;
    private static final Logger log = LoggerFactory.getLogger(LoginSessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UamUserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final UserDeviceRepository deviceRepository;
    private final RefreshTokenServiceUseCase refreshTokenService;
    private final JwtService jwtService;
    private final UamProperties properties;
    private final UserAgentParser userAgentParser;
    private final ApplicationAccessServiceUseCase applicationAccessService;
    private final AuthAuditService authAuditService;
    private final TenantMembershipServiceUseCase membershipService;
    private final TenantApplicationServiceUseCase tenantApplications;
    private final HttpServletRequest httpRequest;
    private final OrgWideAccess orgWideAccess;
    private final TenantRepository tenantRepository;
    private final UserCredentialServiceUseCase credentialService;

    public LoginSessionService(UamUserRepository userRepository,
                               UserSessionRepository sessionRepository,
                               UserDeviceRepository deviceRepository,
                               RefreshTokenServiceUseCase refreshTokenService,
                               JwtService jwtService,
                               UamProperties properties,
                               UserAgentParser userAgentParser,
                               ApplicationAccessServiceUseCase applicationAccessService,
                               AuthAuditService authAuditService,
                               TenantMembershipServiceUseCase membershipService,
                               TenantApplicationServiceUseCase tenantApplications,
                               HttpServletRequest httpRequest,
                               OrgWideAccess orgWideAccess,
                               TenantRepository tenantRepository,
                               UserCredentialServiceUseCase credentialService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.deviceRepository = deviceRepository;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.properties = properties;
        this.userAgentParser = userAgentParser;
        this.applicationAccessService = applicationAccessService;
        this.authAuditService = authAuditService;
        this.membershipService = membershipService;
        this.tenantApplications = tenantApplications;
        this.httpRequest = httpRequest;
        this.orgWideAccess = orgWideAccess;
        this.tenantRepository = tenantRepository;
        this.credentialService = credentialService;
    }

    /** Resolves which user + refresh-token record a raw refresh token belongs to, without
     *  rotating anything yet -- lets the caller make the "is this refresh allowed" business
     *  decision (e.g. account still ACTIVE) before any mutation happens. */
    public Optional<ResolvedRefresh> resolveForRefresh(String rawRefreshToken) {
        return refreshTokenService.findRedeemableByHash(new RefreshTokenServiceUseCase.FindRedeemableByHashInput(sha256Hex(rawRefreshToken)))
                .token()
                // Reuse detection comes before every other check (account status, organization, application), so a stolen
                // token replayed late always revokes its family, whatever else would have refused the request.
                .filter(existing -> !refreshTokenService.revokeFamilyOnReuse(existing))
                .flatMap(existing -> userRepository.findById(existing.getPrincipalId())
                        .map(user -> new ResolvedRefresh(user, existing, rawRefreshToken)));
    }

    /**
     * Refuses, with the same 403 sign-in gives, a user who cannot open the application the request names (the
     * {@value AuthConstants#APPLICATION_CODE_HEADER} header). Lets a one-time-code login check this before it spends the code, so a refusal does
     * not use the code up and the person can try again with the right application.
     */
    public void requireAccessToRequestedApplication(UamUser user) {
        String tenantId = membershipService.resolveForLogin(user, requestedOrganization(user));
        resolveApplicationsAndEnforceRequested(user, tenantId, jwtService.sessionAccess(user, tenantId));
    }

    /** Builds a brand-new session + refresh token + JWT for a just-authenticated user (an interactive sign-in). */
    public LoginResponse issue(UamUser user, String ip, String userAgent, boolean mfaVerified) {
        return issue(user, ip, userAgent, mfaVerified, null);
    }

    /**
     * Same as {@link #issue(UamUser, String, String, boolean)}, recording how the person signed in on the session
     * ({@code authMethod}: null for an interactive sign-in, {@value AuthConstants#AUTH_METHOD_BIOMETRIC} for a device-key one).
     * A biometric session's token carries {@code amr: ["biometric"]}, and keeps it at every refresh.
     */
    public LoginResponse issue(UamUser user, String ip, String userAgent, boolean mfaVerified, String authMethod) {
        String tenantId = membershipService.resolveForLogin(user, requestedOrganization(user));
        // The franchise lookup comes before anything is created: a franchise role without an active franchise gives nothing,
        // and a person left with no access is refused here (a franchise service that is down fails the sign-in, never "inactive").
        SessionAccess access = jwtService.sessionAccess(user, tenantId);
        List<String> applications = resolveApplicationsAndEnforceRequested(user, tenantId, access);
        var effectivePolicy = policyResolver == null ? null : policyResolver.forApplications(tenantId, applications);
        if (policyEnforcer != null) {
            try {
                policyEnforcer.requireLogin(user, effectivePolicy, mfaVerified);
            } catch (UamException required) {
                if ("MFA_ENROLLMENT_REQUIRED".equals(required.getErrorCode()) && enrollment != null)
                    throw new com.heuristiq.uam.securitypolicy.SecurityPolicyEnrollmentRequired(enrollment.getObject().challenge(user));
                throw required;
            }
            policyEnforcer.requireSessionSlot(user.getId(), effectivePolicy);
        }
        ParsedUserAgent ua = userAgentParser.parse(userAgent);
        UserDevice device = findOrCreateDevice(user.getId(), ip, userAgent, ua);

        String rawRefreshToken = generateRefreshToken();
        String refreshTokenHash = sha256Hex(rawRefreshToken);
        UserSession session = createSession(user, device.getId(), ip, userAgent, mfaVerified, ua, refreshTokenHash, effectivePolicy,
                authMethod);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusDays(properties.getAuth().getRefreshExpiryDays());
        refreshTokenService.issue(new RefreshTokenServiceUseCase.IssueInput(user.getId(), UamEnums.PrincipalType.USER, AuthConstants.REFRESH_TOKEN_CLIENT_TYPE,
                refreshTokenHash, session.getSessionId(), device.getId(), refreshExpiresAt, ip, userAgent));

        return buildResponse(user, token(user, applications, tenantId, access, session.getSessionId(), authenticationMethods(authMethod)),
                rawRefreshToken, tenantId, access);
    }

    /** Rotates an already-resolved refresh token: old one revoked, new one issued, linked
     *  session kept alive, fresh access token minted. */
    public LoginResponse rotate(ResolvedRefresh resolved) {
        UamUser user = resolved.user();
        RefreshToken existing = resolved.token();
        UserSession policySession = policyEnforcer == null ? null : policyEnforcer.requireSession(existing.getSessionId(), user.getId());
        // Recomputed on every refresh, so a revoked grant stops working at the next refresh. Only an organization-wide caller
        // may name another organization; for everyone else the session stays in the account's own.
        String tenantId = membershipService.resolveForLogin(user, requestedOrganization(user));
        // Franchise status too: a franchise closed after sign-in takes the franchise role away at this refresh, or refuses it.
        SessionAccess access = jwtService.sessionAccess(user, tenantId);
        List<String> applications = resolveApplicationsAndEnforceRequested(user, tenantId, access);
        if (policySession != null) {
            String policyTenant = String.valueOf(policySession.getMetadata().getOrDefault("policyTenantId", ""));
            var effective = policyResolver.forApplications(tenantId, applications);
            if (!java.util.Objects.equals(policyTenant, tenantId == null ? "" : tenantId)) {
                policyEnforcer.requireLogin(user, effective, Boolean.TRUE.equals(policySession.getMfaVerified()));
                LocalDateTime absolute = policySession.getLoginAt().plusMinutes(effective.number("sessionPolicy.absoluteTimeoutMinutes"));
                if (!absolute.isAfter(LocalDateTime.now()))
                    throw new UamException("Session expired; sign in again", HttpStatus.UNAUTHORIZED);
                policySession.setMetadata(policyEnforcer.sessionMetadata(user, effective));
                if (policySession.getExpiresAt() == null || absolute.isBefore(policySession.getExpiresAt()))
                    policySession.setExpiresAt(absolute);
                // The original login time and the shorter expiry survive an organization switch.
                sessionRepository.save(policySession);
                policyEnforcer.requireSession(existing.getSessionId(), user.getId());
            } else if (!effective.fingerprint().equals(policySession.getMetadata().get("policyFingerprint")))
                throw new UamException("Application access or security policy changed; sign in again", HttpStatus.UNAUTHORIZED);
        }

        if (!StringUtils.hasText(resolved.rawToken())) {
            throw RefreshTokenFailures.invalid();
        }
        LocalDateTime newExpiresAt = LocalDateTime.now().plusDays(properties.getAuth().getRefreshExpiryDays());
        // Either a fresh successor, or (a replay inside the grace window) the family's current one: never a new branch.
        RefreshTokenServiceUseCase.RotateOutput rotated = refreshTokenService.rotate(
                new RefreshTokenServiceUseCase.RotateInput(existing, resolved.rawToken(), newExpiresAt));
        String newHash = rotated.token().getTokenHash();

        String authMethod = touchSession(existing.getSessionId(), newHash);

        return buildResponse(user, token(user, applications, tenantId, access, existing.getSessionId(), authenticationMethods(authMethod)),
                rotated.rawToken(), tenantId, access);
    }

    /**
     * Keeps the sign-in session pointing at the family's current refresh token and extends it. Two racing refreshes of
     * one session both write the same successor hash, so when the versioned save loses to the other one the session is
     * already right and the conflict is ignored.
     *
     * @return the session's auth method (null when it is not found or was signed in interactively)
     */
    private String touchSession(String sessionId, String refreshTokenHash) {
        LocalDateTime now = LocalDateTime.now();
        Optional<UserSession> found = sessionRepository.findBySessionId(sessionId);
        found.ifPresent(session -> {
            session.setRefreshTokenHash(refreshTokenHash);
            session.setLastActivityAt(now);
            if (policyEnforcer == null) session.setExpiresAt(now.plusMinutes(properties.getAuth().getExpiryMinutes()));
            session.setUpdatedAt(now);
            try {
                sessionRepository.save(session);
            } catch (OptimisticLockingFailureException concurrentRefresh) {
                log.debug("Session {} was updated by a concurrent refresh; keeping that update", sessionId);
            }
        });
        return found == null ? null : found.map(UserSession::getAuthMethod).orElse(null);
    }

    /** The organization the caller asked to work in, honoured only for an organization-wide account (null for everyone else). */
    private String requestedOrganization(UamUser user) {
        return orgWideAccess.isOrgWide(user) ? httpRequest.getHeader(TenancyConstants.ORGANIZATION_HEADER) : null;
    }

    /** A refresh token found for a presented raw value; {@code rawToken} is what the caller presented (needed to derive its successor). */
    public record ResolvedRefresh(UamUser user, RefreshToken token, String rawToken) {
        public ResolvedRefresh(UamUser user, RefreshToken token) {
            this(user, token, null);
        }
    }

    /**
     * The applications the user may sign in to, for the token's {@code apps} claim, from the roles the session keeps
     * ({@code access}): a franchise role left out for want of an active franchise opens no application. When the caller
     * names the application it is signing in to ({@value AuthConstants#APPLICATION_CODE_HEADER}) the user must hold access
     * to it, or the login/refresh is refused before any session is created. A caller that sends no header is not
     * refused: existing apps keep working unchanged, and an application's own backend enforces the claim.
     * A person whose franchise roles were left out is refused with {@value AuthConstants#ERROR_FRANCHISE_INACTIVE} when no
     * role is left, or when only those roles gave access to the named application.
     */
    private List<String> resolveApplicationsAndEnforceRequested(UamUser user, String tenantId, SessionAccess access) {
        if (access.franchiseRolesDropped() && access.roles().isEmpty()) {
            throw franchiseInactive(user);
        }
        List<String> applications = applicationsFor(user, access, tenantId);
        String requested = httpRequest.getHeader(AuthConstants.APPLICATION_CODE_HEADER);
        if (StringUtils.hasText(requested) && !containsApplication(applications, requested)) {
            if (access.franchiseRolesDropped()
                    && containsApplication(applicationsFor(user, SessionAccess.unchanged(user.getRoles()), tenantId), requested)) {
                throw franchiseInactive(user);
            }
            authAuditService.loginFailed(user.getUserName(), "No access to application " + requested.trim());
            throw new UamException("You do not have access to this application", HttpStatus.FORBIDDEN);
        }
        return applications;
    }

    /**
     * The applications the roles in {@code access} (and the user's own grants) open, limited to the organization's
     * subscriptions when those are enforced. The stored account is never changed: when roles were left out, a detached copy
     * carrying only the kept roles is resolved.
     */
    private List<String> applicationsFor(UamUser user, SessionAccess access, String tenantId) {
        UamUser holder = !access.franchiseRolesDropped() ? user
                : UamUser.builder().id(user.getId()).tenantId(user.getTenantId()).userName(user.getUserName()).roles(access.roles()).build();
        List<String> applications = applicationAccessService
                .resolveAccessibleApplications(new ApplicationAccessServiceUseCase.ResolveInput(holder)).applicationCodes();
        if (properties.getTenancy().isEnforceSubscriptions() && tenantId != null && !isSuperAdmin(user)) {
            List<String> subscribed = tenantApplications.activeApplicationCodes(tenantId);
            applications = applications.stream().filter(subscribed::contains).toList();
        }
        return applications;
    }

    private static boolean containsApplication(List<String> applications, String requested) {
        return applications.stream().anyMatch(code -> code.equalsIgnoreCase(requested.trim()));
    }

    /** The refusal of a person whose only access came from franchise roles and who has no active franchise; audited as a failed sign-in. */
    private UamException franchiseInactive(UamUser user) {
        authAuditService.loginFailed(user.getUserName(), AuthConstants.FRANCHISE_INACTIVE_AUDIT_REASON);
        return new UamException(AuthConstants.FRANCHISE_INACTIVE_MESSAGE, HttpStatus.FORBIDDEN, AuthConstants.ERROR_FRANCHISE_INACTIVE);
    }

    private static boolean isSuperAdmin(UamUser user) {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(UamCommonConstants.SUPER_ADMIN_ROLE::equalsIgnoreCase);
    }

    /** The access token of a session; the {@code amr} claim is written only for a biometric session, so other tokens are unchanged. */
    private String token(UamUser user, List<String> applications, String tenantId, SessionAccess access, String sessionId,
                         List<String> authenticationMethods) {
        boolean orgWide = orgWideAccess.isOrgWide(user);
        if (authenticationMethods == null) {
            return policyEnforcer == null ? jwtService.generate(user, applications, tenantId, orgWide, access)
                    : jwtService.generateSession(user, applications, tenantId, orgWide, access, sessionId);
        }
        return jwtService.generateSession(user, applications, tenantId, orgWide, access, policyEnforcer == null ? null : sessionId,
                authenticationMethods);
    }

    /** The {@code amr} values of a session's token: ["biometric"] for a biometric session, null (no claim) otherwise. */
    private static List<String> authenticationMethods(String authMethod) {
        return AuthConstants.AUTH_METHOD_BIOMETRIC.equals(authMethod) ? List.of(AuthConstants.AMR_BIOMETRIC) : null;
    }

    /** The response shows the roles the session keeps ({@code access}), the same ones its token carries. */
    private LoginResponse buildResponse(UamUser user, String token, String rawRefreshToken, String tenantId, SessionAccess access) {
        return LoginResponse.of(user, token, rawRefreshToken, organization(tenantId), access.roles())
                .withForcePasswordChange(mustChangePassword(user));
    }

    /** True while the user's own (local) password is a temporary one that must be replaced before anything else. */
    private boolean mustChangePassword(UamUser user) {
        return credentialService
                .findActiveCredential(new UserCredentialServiceUseCase.FindActiveCredentialInput(user.getId(), UamEnums.AuthProvider.LOCAL))
                .credential()
                .map(credential -> Boolean.TRUE.equals(credential.getForcePasswordChange()))
                .orElse(false);
    }

    /** The organization the token is issued for (the one cached tenant lookup); null when there is none or it is unknown. */
    private Tenant organization(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }
        return tenantRepository.findById(tenantId).orElse(null);
    }

    private UserDevice findOrCreateDevice(String userId, String ip, String userAgent, ParsedUserAgent ua) {
        String fingerprint = fingerprint(userAgent);
        LocalDateTime now = LocalDateTime.now();
        return deviceRepository.findByUserId(userId).stream()
                .filter(d -> fingerprint.equals(d.getDeviceFingerprint()))
                .findFirst()
                .map(d -> {
                    d.setLastLoginAt(now);
                    d.setLastActiveAt(now);
                    d.setLastIpAddress(ip);
                    d.setBrowser(ua.browser());
                    d.setBrowserVersion(ua.browserVersion());
                    d.setOperatingSystem(ua.os());
                    d.setManufacturer(ua.manufacturer());
                    d.setPlatform(ua.platform());
                    d.setDeviceType(ua.deviceType());
                    d.setUpdatedAt(now);
                    return deviceRepository.save(d);
                })
                .orElseGet(() -> deviceRepository.save(UserDevice.builder()
                        .userId(userId)
                        .deviceFingerprint(fingerprint)
                        .deviceName(ua.deviceName())
                        .deviceType(ua.deviceType())
                        .operatingSystem(ua.os())
                        .browser(ua.browser())
                        .browserVersion(ua.browserVersion())
                        .manufacturer(ua.manufacturer())
                        .platform(ua.platform())
                        .trusted(false)
                        .verified(false)
                        .blocked(false)
                        .lastLoginAt(now)
                        .lastActiveAt(now)
                        .lastIpAddress(ip)
                        .registeredAt(now)
                        .active(true)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));
    }

    private UserSession createSession(UamUser user, String deviceId, String ip, String userAgent, boolean mfaVerified,
                                      ParsedUserAgent ua, String refreshTokenHash,
                                      com.heuristiq.uam.securitypolicy.EffectiveSecurityPolicy effective, String authMethod) {
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.save(UserSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .userId(user.getId())
                .deviceId(deviceId)
                .provider(UamEnums.AuthProvider.LOCAL)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ip)
                .userAgent(userAgent)
                .browser(ua.browser())
                .operatingSystem(ua.os())
                .deviceType(ua.deviceType().name())
                .loginAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusMinutes(effective == null ? properties.getAuth().getExpiryMinutes()
                        : effective.number("sessionPolicy.absoluteTimeoutMinutes")))
                .metadata(policyEnforcer == null ? null : policyEnforcer.sessionMetadata(user, effective))
                .active(true)
                .mfaVerified(mfaVerified)
                .authMethod(authMethod)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    // A real, cryptographically random refresh token -- only its SHA-256 hash is ever persisted
    // (RefreshToken.tokenHash / UserSession.refreshTokenHash), matching how passwords are handled.
    private String generateRefreshToken() {
        byte[] bytes = new byte[AuthConstants.REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        return RefreshTokenHashing.sha256Hex(input);
    }

    // Minimal device identity: hash of the raw User-Agent header. Note this means a browser
    // version bump changes the fingerprint too (looks like a "new" device) -- acceptable for now,
    // a real fingerprint would need a client-supplied device id instead.
    private String fingerprint(String userAgent) {
        return com.heuristiq.uam.device.DeviceFingerprint.of(userAgent);
    }
}
