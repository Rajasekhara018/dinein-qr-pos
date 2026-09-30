package com.heuristq.dinein.auth;

import com.heuristq.dinein.auth.domain.RefreshTokenEntity;
import com.heuristq.dinein.auth.domain.RefreshTokenRepository;
import com.heuristq.dinein.auth.dto.AuthDtos.StaffInfo;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.shared.security.jwt.JwtTokenService;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.staff.DeviceTokenService;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {


    private final StaffUserRepository staffUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties.Security securityProps;
    private final Clock clock;
    /** BCrypt hash of a random string; used to equalise timing when the username does not exist. */
    private final String dummyHash;

    public AuthService(StaffUserRepository staffUserRepository, RefreshTokenRepository refreshTokenRepository,
                       DeviceTokenService deviceTokenService, JwtTokenService jwtTokenService,
                       PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.staffUserRepository = staffUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceTokenService = deviceTokenService;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.securityProps = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(SecureTokens.randomUrlSafe(16));
    }

    /**
     * Staff login for the admin panel (OWNER/MANAGER) and the waiter screen (WAITER). Waiters may use their PIN
     * instead of the password, like kitchen accounts do on the kitchen screen. Failed-attempt counters must persist,
     * so ApiException does not roll back.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Session login(String username, String password, String pin) {
        boolean usePin = (password == null || password.isBlank()) && pin != null && !pin.isBlank();
        if (!usePin && (password == null || password.isBlank())) {
            throw ApiException.badRequest("CREDENTIALS_REQUIRED", "Enter your password or PIN");
        }
        StaffUserEntity user = verifyCredentials(username, usePin ? pin : password, usePin);
        if (user.getRole() == StaffRole.KITCHEN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KITCHEN_ACCOUNT", "Kitchen accounts sign in on the kitchen screen");
        }
        if (usePin && user.getRole() != StaffRole.WAITER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PIN_LOGIN_NOT_ALLOWED", "Sign in with your password");
        }
        return startSession(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public KitchenDeviceSession registerKitchenDevice(String username, String password, String pin,
                                                                      String deviceName) {
        boolean usePin = (password == null || password.isBlank()) && pin != null && !pin.isBlank();
        if (!usePin && (password == null || password.isBlank())) {
            throw ApiException.badRequest("CREDENTIALS_REQUIRED", "Enter your password or PIN");
        }
        StaffUserEntity user = verifyCredentials(username, usePin ? pin : password, usePin);
        String name = deviceName == null || deviceName.isBlank() ? "Kitchen screen" : deviceName.trim();
        DeviceTokenService.IssuedDeviceToken issued = deviceTokenService.issue(user, name);
        return new KitchenDeviceSession(issued.token(), issued.expiresAt(), toInfo(user));
    }

    /**
     * Checks a staff member's PIN for the kiosk's service menu. Uses the same lockout as every other login, and only
     * accepts staff of the given restaurant who can run the floor (waiter, manager, owner). Failed-attempt counters
     * must persist, so ApiException does not roll back.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public StaffInfo verifyStaffPinForRestaurant(String username, String pin, Long restaurantId) {
        StaffUserEntity user;
        try {
            user = verifyCredentials(username, pin, true);
        } catch (ApiException e) {
            throw asForbidden(e);
        }
        boolean allowedRole = user.getRole() == StaffRole.WAITER || user.getRole() == StaffRole.MANAGER
                || user.getRole() == StaffRole.OWNER;
        if (!allowedRole || !restaurantId.equals(user.getRestaurantId())) {
            // Same answer as a wrong PIN: do not reveal that the account exists elsewhere or has another role.
            throw asForbidden(invalidCredentials());
        }
        return toInfo(user);
    }

    public StaffInfo infoFor(Long userId) {
        return toInfo(staffUserRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
    }

    /** Reuse detection revokes the whole family and then throws, so ApiException must not roll that back. */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthorized();
        }
        Instant now = clock.instant();
        RefreshTokenEntity current = refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .orElseThrow(this::unauthorized);
        if (current.getRevokedAt() != null) {
            // An already-rotated token was presented again: assume theft and kill the whole login family.
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            log.warn("auth.refresh.reuse_detected userId={} family={}", current.getStaffUserId(), current.getFamilyId());
            throw unauthorized();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            throw unauthorized();
        }
        StaffUserEntity user = staffUserRepository.findById(current.getStaffUserId())
                .filter(StaffUserEntity::isActive)
                .orElseThrow(this::unauthorized);
        current.setRevokedAt(now);
        return issueSession(user, current.getFamilyId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(SecureTokens.sha256Hex(rawRefreshToken))
                .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional
    public Session changePassword(StaffPrincipal principal, String currentPassword, String newPassword) {
        if (principal.isDevice()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not available on kitchen devices");
        }
        StaffUserEntity user = staffUserRepository.findById(principal.userId()).orElseThrow(this::unauthorized);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_REUSED", "New password must differ from the current one");
        }
        PasswordPolicy.validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
        log.info("auth.password_changed userId={}", user.getId());
        return startSession(user);
    }

    @Scheduled(cron = "0 15 4 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int removed = refreshTokenRepository.deleteExpired(clock.instant().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("auth.refresh_tokens.purged count={}", removed);
        }
    }

    private StaffUserEntity verifyCredentials(String username, String secret, boolean usePin) {
        Instant now = clock.instant();
        StaffUserEntity user = staffUserRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (user == null || !user.isActive()) {
            passwordEncoder.matches(secret, dummyHash);
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Too many failed attempts. Try again in " + minutes + " minute(s).");
        }
        String hash = usePin ? user.getPinHash() : user.getPasswordHash();
        boolean ok = hash != null && passwordEncoder.matches(secret, hash);
        if (!ok) {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= securityProps.maxFailedLogins()) {
                user.setLockedUntil(now.plus(securityProps.lockoutDuration()));
                user.setFailedLoginAttempts(0);
                log.warn("auth.account_locked userId={}", user.getId());
            } else {
                user.setFailedLoginAttempts(attempts);
            }
            log.info("auth.login_failed userId={} attempts={}", user.getId(), attempts);
            throw invalidCredentials();
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        log.info("auth.login_ok userId={} role={} pin={}", user.getId(), user.getRole(), usePin);
        return user;
    }

    private Session startSession(StaffUserEntity user) {
        return issueSession(user, UUID.randomUUID().toString());
    }

    private Session issueSession(StaffUserEntity user, String familyId) {
        String raw = SecureTokens.randomUrlSafe(32);
        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setStaffUserId(user.getId());
        token.setTokenHash(SecureTokens.sha256Hex(raw));
        token.setFamilyId(familyId);
        token.setExpiresAt(clock.instant().plus(securityProps.refreshTokenTtl()));
        refreshTokenRepository.save(token);
        return new Session(jwtTokenService.issueAccessToken(user), jwtTokenService.accessTokenTtlSeconds(), raw,
                securityProps.refreshTokenTtl(), toInfo(user));
    }

    public static StaffInfo toInfo(StaffUserEntity user) {
        return new StaffInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                user.isMustChangePassword(), user.isPlatformAdmin(), user.getRestaurantId());
    }

    /**
     * The kiosk app treats a 401 as "my device token was revoked" and unpairs itself, so a wrong staff PIN must not
     * be a 401 there. Lockout (423) and other statuses pass through unchanged.
     */
    private static ApiException asForbidden(ApiException e) {
        return e.getStatus() == HttpStatus.UNAUTHORIZED
                ? new ApiException(HttpStatus.FORBIDDEN, e.getCode(), e.getMessage()) : e;
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Session expired. Please log in again.");
    }

    public record KitchenDeviceSession(String token, Instant expiresAt, StaffInfo user) {
    }

    public record Session(String accessToken, long expiresIn, String refreshToken, Duration refreshTtl, StaffInfo user) {
    }
}
