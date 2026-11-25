package com.heuristiq.identity.securitypolicy;

import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.passwordsHistory.PasswordHistoryRepository;
import com.heuristiq.identity.security.PasswordService;
import com.heuristiq.identity.session.UserSession;
import com.heuristiq.identity.session.repository.UserSessionRepository;
import com.heuristiq.identity.usercredentials.UserCredentialRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** The enforcement counterpart of the effective-policy preview. */
@Service
public class SecurityPolicyEnforcer {
    private final EffectiveSecurityPolicyResolver resolver;
    private final UamUserRepository users;
    private final UserCredentialRepository credentials;
    private final PasswordHistoryRepository history;
    private final PasswordService passwords;
    private final UserSessionRepository sessions;
    private final ApplicationAccessServiceUseCase applications;

    public SecurityPolicyEnforcer(EffectiveSecurityPolicyResolver resolver, UamUserRepository users,
            UserCredentialRepository credentials, PasswordHistoryRepository history, PasswordService passwords,
            UserSessionRepository sessions, ApplicationAccessServiceUseCase applications) {
        this.resolver = resolver; this.users = users; this.credentials = credentials; this.history = history;
        this.passwords = passwords; this.sessions = sessions; this.applications = applications;
    }

    public EffectiveSecurityPolicy forUser(UamUser user) {
        return resolver.forApplications(user.getTenantId(), applications.resolveAccessibleApplications(
                new ApplicationAccessServiceUseCase.ResolveInput(user)).applicationCodes());
    }

    public EffectiveSecurityPolicy forUsername(String username) {
        return users.findByUserNameIgnoreCase(username == null ? "" : username.trim())
                .or(() -> users.findByEmailIgnoreCase(username == null ? "" : username.trim()))
                .map(this::forUser).orElseGet(() -> resolver.resolve(null, null));
    }

    public void requirePassword(String userId, String password, boolean temporary) {
        requirePassword(userId, password, temporary, false);
    }

    public void requirePassword(String userId, String password, boolean temporary, boolean verifiedRecovery) {
        UamUser user = users.findById(userId).orElseThrow(() -> UamException.notFound("User not found"));
        EffectiveSecurityPolicy policy = forUser(user);
        if (password == null || password.length() < policy.number("passwordPolicy.minimumLength")
                || password.length() > policy.number("passwordPolicy.maximumLength"))
            throw new UamException("Password length does not meet the effective security policy");
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new UamException("Password must fit the password storage limit of 72 UTF-8 bytes");
        requireCharacter(policy, password, "requireUppercase", Character::isUpperCase);
        requireCharacter(policy, password, "requireLowercase", Character::isLowerCase);
        requireCharacter(policy, password, "requireNumber", Character::isDigit);
        requireCharacter(policy, password, "requireSpecialCharacter", c -> !Character.isLetterOrDigit(c));
        if (policy.enabled("passwordPolicy.preventUsernameInPassword") && user.getUserName() != null
                && password.toLowerCase(Locale.ROOT).contains(user.getUserName().toLowerCase(Locale.ROOT)))
            throw new UamException("Password must not contain the username");
        var current = credentials.findByUserIdAndProvider(userId, UamEnums.AuthProvider.LOCAL);
        int minimumAge = policy.number("passwordPolicy.minimumAgeDays");
        if (!temporary && !verifiedRecovery && minimumAge > 0 && current.filter(c -> !Boolean.TRUE.equals(c.getForcePasswordChange())
                && c.getPasswordChangedAt() != null && c.getPasswordChangedAt().plusDays(minimumAge).isAfter(LocalDateTime.now())).isPresent())
            throw new UamException("Password was changed too recently");
        int count = policy.number("passwordPolicy.passwordHistoryCount");
        if (count > 0 && history.findByUserId(userId, PageRequest.of(0, Math.min(count, 100), Sort.by("audit.createdAt").descending()))
                .stream().anyMatch(old -> old.getPasswordHash() != null && passwords.matches(password, old.getPasswordHash())))
            throw new UamException("Password must not repeat a recently used password");
    }

    private static void requireCharacter(EffectiveSecurityPolicy policy, String password, String field,
                                         java.util.function.IntPredicate predicate) {
        if (policy.enabled("passwordPolicy." + field) && password.codePoints().noneMatch(predicate))
            throw new UamException("Password does not meet " + field);
    }

    public boolean methodAllowed(UamUser user, UamEnums.MfaType type) {
        EffectiveSecurityPolicy policy = forUser(user);
        return switch (type) {
            case TOTP -> policy.enabled("mfaPolicy.authenticatorAppAllowed");
            case EMAIL_OTP -> policy.enabled("mfaPolicy.emailOtpAllowed");
            case SMS_OTP -> policy.enabled("mfaPolicy.smsOtpAllowed");
            default -> false;
        };
    }

    public void requireLogin(UamUser user, EffectiveSecurityPolicy policy, boolean mfaVerified) {
        int expiryDays = policy.number("passwordPolicy.expiryDays");
        if (expiryDays > 0 && credentials.findByUserIdAndProvider(user.getId(), UamEnums.AuthProvider.LOCAL)
                .filter(c -> c.getPasswordChangedAt() != null && !c.getPasswordChangedAt().plusDays(expiryDays).isAfter(LocalDateTime.now())).isPresent())
            throw new UamException("Your password has expired; reset it before signing in", HttpStatus.FORBIDDEN, "PASSWORD_EXPIRED");
        if (policy.enabled("mfaPolicy.mandatory") && !mfaVerified)
            throw new UamException("MFA enrollment and verification are required by your security policy", HttpStatus.FORBIDDEN,
                    "MFA_ENROLLMENT_REQUIRED");
        var all = credentials.findByUserId(user.getId());
        if (policy.enabled("accountPolicy.emailVerificationRequired") && all.stream().noneMatch(c -> Boolean.TRUE.equals(c.getActive()) && Boolean.TRUE.equals(c.getEmailVerified())))
            throw new UamException("Verify your email address before signing in", HttpStatus.FORBIDDEN, "EMAIL_VERIFICATION_REQUIRED");
        if (policy.enabled("accountPolicy.phoneVerificationRequired") && all.stream().noneMatch(c -> Boolean.TRUE.equals(c.getActive()) && Boolean.TRUE.equals(c.getPhoneVerified())))
            throw new UamException("Verify your phone number before signing in", HttpStatus.FORBIDDEN, "PHONE_VERIFICATION_REQUIRED");
    }

    public int sessionLimit(EffectiveSecurityPolicy policy) {
        return policy.enabled("sessionPolicy.singleSessionOnly") || !policy.enabled("loginPolicy.allowConcurrentSessions")
                ? 1 : policy.number("loginPolicy.maxConcurrentSessions");
    }

    public void requireSessionSlot(String userId, EffectiveSecurityPolicy policy) {
        int limit = sessionLimit(policy);
        if (limit > 0 && activeSessions(userId).stream().filter(s -> s.getLastActivityAt() != null
                && s.getLastActivityAt().plusMinutes(policy.number("sessionPolicy.idleTimeoutMinutes")).isAfter(LocalDateTime.now())).count() >= limit)
            throw new UamException("The security policy session limit has been reached; sign out another device first",
                    HttpStatus.FORBIDDEN, "SESSION_LIMIT_REACHED");
    }

    private List<UserSession> activeSessions(String userId) {
        LocalDateTime now = LocalDateTime.now();
        UamUser user = users.findById(userId).orElse(null);
        if (user == null) return List.of();
        LocalDateTime changed = credentials.findByUserIdAndProvider(userId, UamEnums.AuthProvider.LOCAL)
                .map(c -> c.getPasswordChangedAt()).orElse(null);
        return sessions.findByUserIdAndActiveTrue(userId).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getRevoked()) && !Boolean.TRUE.equals(s.getForcedLogout())
                        && (s.getExpiresAt() == null || s.getExpiresAt().isAfter(now)))
                .filter(s -> validForSessionLimit(s, user, changed, now))
                .sorted(Comparator.comparing(UserSession::getLoginAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(UserSession::getSessionId)).toList();
    }

    private boolean validForSessionLimit(UserSession session, UamUser user, LocalDateTime passwordChanged, LocalDateTime now) {
        var meta = session.getMetadata();
        if (meta == null || !(meta.get("policyApplicationIds") instanceof List<?> ids)
                || session.getLoginAt() == null || session.getLastActivityAt() == null) return false;
        var policy = resolver.forApplicationIds(String.valueOf(meta.getOrDefault("policyTenantId", "")), ids.stream().map(String::valueOf).toList());
        return policy.fingerprint().equals(meta.get("policyFingerprint"))
                && session.getLastActivityAt().plusMinutes(policy.number("sessionPolicy.idleTimeoutMinutes")).isAfter(now)
                && session.getLoginAt().plusMinutes(policy.number("sessionPolicy.absoluteTimeoutMinutes")).isAfter(now)
                && (!policy.enabled("sessionPolicy.invalidateOnRoleChange") || roles(user).equals(meta.get("policyRoles")))
                && (!policy.enabled("sessionPolicy.invalidateOnPasswordChange") || passwordChanged == null || !passwordChanged.isAfter(session.getLoginAt()));
    }

    public Map<String, Object> sessionMetadata(UamUser user, EffectiveSecurityPolicy policy) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("policyTenantId", policy.tenantId() == null ? "" : policy.tenantId());
        metadata.put("policyApplicationIds", policy.applicationIds());
        metadata.put("policyFingerprint", policy.fingerprint());
        metadata.put("policyRoles", roles(user));
        return metadata;
    }

    private static List<String> roles(UamUser user) { return user.getRoles() == null ? List.of() : user.getRoles().stream().sorted().toList(); }

    @SuppressWarnings("unchecked")
    public UserSession requireSession(String sessionId, String userId) {
        UserSession session = sessions.findBySessionId(sessionId).orElseThrow(SecurityPolicyEnforcer::expired);
        if (!Objects.equals(userId, session.getUserId()) || !Boolean.TRUE.equals(session.getActive())
                || Boolean.TRUE.equals(session.getRevoked()) || Boolean.TRUE.equals(session.getForcedLogout())) throw expired();
        Map<String, Object> meta = session.getMetadata();
        if (meta == null || !(meta.get("policyApplicationIds") instanceof List<?>)) throw expired();
        EffectiveSecurityPolicy policy = resolver.forApplicationIds(String.valueOf(meta.getOrDefault("policyTenantId", "")),
                ((List<?>) meta.get("policyApplicationIds")).stream().map(String::valueOf).toList());
        LocalDateTime now = LocalDateTime.now();
        if (!policy.fingerprint().equals(meta.get("policyFingerprint")) || session.getLoginAt() == null
                || session.getLoginAt().plusMinutes(policy.number("sessionPolicy.absoluteTimeoutMinutes")).isBefore(now)
                || session.getLastActivityAt() == null
                || session.getLastActivityAt().plusMinutes(policy.number("sessionPolicy.idleTimeoutMinutes")).isBefore(now)) throw expired();
        UamUser user = users.findById(userId).orElseThrow(SecurityPolicyEnforcer::expired);
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) throw expired();
        if (policy.enabled("sessionPolicy.invalidateOnRoleChange") && !roles(user).equals(meta.get("policyRoles"))) throw expired();
        if (policy.enabled("sessionPolicy.invalidateOnPasswordChange") && credentials.findByUserIdAndProvider(userId, UamEnums.AuthProvider.LOCAL)
                .filter(c -> c.getPasswordChangedAt() != null && c.getPasswordChangedAt().isAfter(session.getLoginAt())).isPresent()) throw expired();
        if (policy.enabled("mfaPolicy.mandatory") && !Boolean.TRUE.equals(session.getMfaVerified())) throw expired();
        requireLogin(user, policy, Boolean.TRUE.equals(session.getMfaVerified()));
        int limit = sessionLimit(policy);
        if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) throw expired();
        if (limit > 0 && activeSessions(userId).stream().filter(s -> s.getLastActivityAt() != null
                && s.getLastActivityAt().plusMinutes(policy.number("sessionPolicy.idleTimeoutMinutes")).isAfter(now))
                .limit(limit).noneMatch(s -> sessionId.equals(s.getSessionId()))) throw expired();
        if (session.getLastActivityAt().plusSeconds(15).isBefore(now)) {
            session.setLastActivityAt(now);
            try { sessions.save(session); } catch (org.springframework.dao.OptimisticLockingFailureException racedActivity) { /* another request touched it */ }
        }
        return session;
    }

    private static UamException expired() {
        return new UamException("Session expired or security policy changed; sign in again", HttpStatus.UNAUTHORIZED, "SESSION_REAUTHENTICATION_REQUIRED");
    }
}
