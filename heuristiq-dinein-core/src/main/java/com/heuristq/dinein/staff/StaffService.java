package com.heuristq.dinein.staff;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.auth.PasswordPolicy;
import com.heuristq.dinein.auth.domain.RefreshTokenRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.StaffPrincipal;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.staff.dto.StaffRequests.CreateStaff;
import com.heuristq.dinein.staff.dto.StaffRequests.UpdateStaff;
import com.heuristq.dinein.staff.dto.StaffResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class StaffService {

    private final StaffUserRepository staffUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceTokenService deviceTokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final AuditService auditService;

    public StaffService(StaffUserRepository staffUserRepository, RefreshTokenRepository refreshTokenRepository,
                        DeviceTokenService deviceTokenService, PasswordEncoder passwordEncoder, Clock clock,
                        AuditService auditService) {
        this.staffUserRepository = staffUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.deviceTokenService = deviceTokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> list() {
        return staffUserRepository.findAllByRestaurantIdOrderByUsernameAsc(currentRestaurantId()).stream()
                .map(StaffResponse::from).toList();
    }

    @Transactional
    public StaffResponse create(CreateStaff request) {
        String username = request.username().trim();
        if (staffUserRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already in use");
        }
        PasswordPolicy.validate(request.password());
        StaffUserEntity user = new StaffUserEntity();
        user.setRestaurantId(currentRestaurantId());
        user.setUsername(username);
        user.setDisplayName(trimToNull(request.displayName()));
        user.setEmail(normalizeEmail(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setRole(request.role());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (request.pin() != null && !request.pin().isBlank()) {
            user.setPinHash(passwordEncoder.encode(request.pin()));
        }
        // Admin-set passwords are temporary: the person must choose their own on first login. Kitchen and waiter
        // accounts are exempt: they usually sign in with a PIN on shared screens.
        user.setMustChangePassword(!usesPinSignIn(request.role()));
        staffUserRepository.save(user);
        log.info("staff.created userId={} role={}", user.getId(), user.getRole());
        auditService.record("STAFF_CREATED", "StaffUser", user.getId(), null, user.getRole().name());
        return StaffResponse.from(user);
    }

    @Transactional
    public StaffResponse update(Long id, UpdateStaff request, StaffPrincipal actor) {
        StaffUserEntity user = staffUserRepository.findByIdAndRestaurantId(id, actor.restaurantId())
                .orElseThrow(() -> ApiException.notFound("Staff user"));
        boolean selfEdit = user.getId().equals(actor.userId());
        if (selfEdit && (!request.active() || request.role() != user.getRole())) {
            throw ApiException.badRequest("SELF_LOCKOUT", "You cannot deactivate yourself or change your own role");
        }
        StaffRole previousRole = user.getRole();
        user.setDisplayName(trimToNull(request.displayName()));
        user.setEmail(normalizeEmail(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setRole(request.role());
        boolean deactivated = user.isActive() && !request.active();
        user.setActive(request.active());
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            PasswordPolicy.validate(request.newPassword());
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            user.setMustChangePassword(!selfEdit && !usesPinSignIn(user.getRole()));
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
        }
        if (request.clearPin()) {
            user.setPinHash(null);
        } else if (request.pin() != null && !request.pin().isBlank()) {
            user.setPinHash(passwordEncoder.encode(request.pin()));
        }
        if (deactivated) {
            refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
            deviceTokenService.revokeAllForUser(user.getId());
        }
        if (user.getRole() != StaffRole.OWNER
                && !staffUserRepository.existsByRestaurantIdAndRole(actor.restaurantId(), StaffRole.OWNER)) {
            throw ApiException.badRequest("LAST_OWNER", "At least one owner account must remain");
        }
        log.info("staff.updated userId={} by={}", user.getId(), actor.userId());
        if (previousRole != user.getRole()) {
            auditService.record("STAFF_ROLE_CHANGED", "StaffUser", user.getId(), previousRole.name(), user.getRole().name());
        }
        if (deactivated) {
            auditService.record("STAFF_DEACTIVATED", "StaffUser", user.getId(), null, null);
        }
        return StaffResponse.from(user);
    }

    private static Long currentRestaurantId() {
        return CurrentStaff.require().restaurantId();
    }

    private static boolean usesPinSignIn(StaffRole role) {
        return role == StaffRole.KITCHEN || role == StaffRole.WAITER;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeEmail(String value) {
        String email = trimToNull(value);
        return email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
