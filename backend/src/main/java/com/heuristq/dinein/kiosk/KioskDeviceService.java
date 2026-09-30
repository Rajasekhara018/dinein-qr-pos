package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.kiosk.domain.KioskDeviceEntity;
import com.heuristq.dinein.kiosk.domain.KioskDeviceRepository;
import com.heuristq.dinein.kiosk.dto.KioskDtos.KioskDeviceView;
import com.heuristq.dinein.kiosk.dto.KioskDtos.PairResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.PairingCodeResponse;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.security.KioskPrincipal;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.shared.util.Text;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lifecycle of a kiosk device: admin issues a one-time pairing code, the kiosk exchanges it for a long-lived
 * revocable token ({@code kdt_...}). Only SHA-256 hashes of the code and the token are stored.
 */
@Slf4j
@Service
public class KioskDeviceService {

    public static final String TOKEN_PREFIX = "kdt_";

    private static final Duration PAIRING_TTL = Duration.ofMinutes(15);
    private static final Duration LAST_SEEN_RESOLUTION = Duration.ofMinutes(5);
    private static final int CODE_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KioskDeviceRepository repository;
    private final SettingsService settingsService;
    private final AuditService auditService;
    private final Clock clock;

    public KioskDeviceService(KioskDeviceRepository repository, SettingsService settingsService,
                              AuditService auditService, Clock clock) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ----- Admin ---------------------------------------------------------------------------------

    @Transactional
    public PairingCodeResponse create(String name) {
        KioskDeviceEntity device = new KioskDeviceEntity();
        device.setRestaurantId(CurrentStaff.require().restaurantId());
        device.setName(Text.clean(name, 60));
        String code = issueCode(device);
        repository.save(device);
        log.info("kiosk.created deviceId={}", device.getId());
        auditService.record("KIOSK_CREATED", "KioskDevice", device.getId(), null, device.getName());
        return new PairingCodeResponse(device.getId(), device.getName(), code, device.getPairingExpiresAt());
    }

    /** Issues a fresh code and invalidates the device's current token, so a lost or replaced tablet can be re-paired. */
    @Transactional
    public PairingCodeResponse reissueCode(Long id) {
        KioskDeviceEntity device = find(id);
        if (device.isRevoked()) {
            throw ApiException.conflict("KIOSK_REVOKED", "This kiosk was revoked. Create a new one instead.");
        }
        device.setTokenHash(null);
        device.setPairedAt(null);
        String code = issueCode(device);
        log.info("kiosk.repair_code deviceId={}", id);
        auditService.record("KIOSK_REPAIR_CODE", "KioskDevice", id, null, device.getName());
        return new PairingCodeResponse(device.getId(), device.getName(), code, device.getPairingExpiresAt());
    }

    @Transactional(readOnly = true)
    public List<KioskDeviceView> list() {
        Instant now = clock.instant();
        return repository.findAllByRestaurantIdOrderByCreatedAtDesc(CurrentStaff.require().restaurantId()).stream()
                .map(d -> new KioskDeviceView(d.getId(), d.getName(), statusOf(d, now), d.getPairedAt(),
                        d.getLastSeenAt(), d.isPairingOpen(now) ? d.getPairingExpiresAt() : null,
                        d.getApplicationVersion(), d.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void revoke(Long id) {
        KioskDeviceEntity device = find(id);
        if (!device.isRevoked()) {
            device.setRevokedAt(clock.instant());
            device.setPairingCodeHash(null);
            device.setPairingExpiresAt(null);
            log.info("kiosk.revoked deviceId={}", id);
            auditService.record("KIOSK_REVOKED", "KioskDevice", id, null, device.getName());
        }
    }

    // ----- Kiosk ---------------------------------------------------------------------------------

    /** Unauthenticated: the pairing code is the credential. It is single-use and expires after 15 minutes. */
    @Transactional
    public PairResponse pair(String code) {
        Instant now = clock.instant();
        KioskDeviceEntity device = repository.findByPairingCodeHash(SecureTokens.sha256Hex(code))
                .filter(d -> d.isPairingOpen(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_PAIRING_CODE",
                        "That pairing code is not valid or has expired"));
        String raw = TOKEN_PREFIX + SecureTokens.randomUrlSafe(32);
        device.setTokenHash(SecureTokens.sha256Hex(raw));
        device.setPairedAt(now);
        device.setLastSeenAt(now);
        device.setPairingCodeHash(null);
        device.setPairingExpiresAt(null);
        // The name the restaurant set in Settings: the same one the branding and printed bills show.
        String restaurantName = settingsService.forRestaurant(device.getRestaurantId()).getName();
        log.info("kiosk.paired deviceId={}", device.getId());
        auditService.recordForRestaurant(device.getRestaurantId(), "KIOSK_PAIRED", "KioskDevice", device.getId(),
                null, device.getName());
        return new PairResponse(raw, device.getId(), restaurantName);
    }

    @Transactional
    public Optional<KioskPrincipal> authenticate(String rawToken) {
        Instant now = clock.instant();
        return repository.findByTokenHash(SecureTokens.sha256Hex(rawToken))
                .filter(d -> !d.isRevoked())
                .map(device -> {
                    if (device.getLastSeenAt() == null
                            || device.getLastSeenAt().plus(LAST_SEEN_RESOLUTION).isBefore(now)) {
                        device.setLastSeenAt(now);
                    }
                    return new KioskPrincipal(device.getId(), device.getRestaurantId(), device.getName());
                });
    }

    @Transactional
    public void heartbeat(Long deviceId, String applicationVersion) {
        repository.findById(deviceId).ifPresent(device -> {
            device.setLastSeenAt(clock.instant());
            if (applicationVersion != null && !applicationVersion.isBlank()) {
                device.setApplicationVersion(applicationVersion.length() > 20
                        ? applicationVersion.substring(0, 20) : applicationVersion);
            }
        });
    }

    // ----- helpers -------------------------------------------------------------------------------

    private KioskDeviceEntity find(Long id) {
        return repository.findByIdAndRestaurantId(id, CurrentStaff.require().restaurantId())
                .orElseThrow(() -> ApiException.notFound("Kiosk"));
    }

    /** Sets a fresh, currently unused code on the device and returns it in plain text (once). */
    private String issueCode(KioskDeviceEntity device) {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            String hash = SecureTokens.sha256Hex(code);
            if (!repository.existsByPairingCodeHash(hash)) {
                device.setPairingCodeHash(hash);
                device.setPairingExpiresAt(clock.instant().plus(PAIRING_TTL));
                return code;
            }
        }
        throw new IllegalStateException("could not allocate a free kiosk pairing code");
    }

    private static String statusOf(KioskDeviceEntity d, Instant now) {
        if (d.isRevoked()) {
            return "REVOKED";
        }
        return d.getTokenHash() == null ? "PENDING_PAIRING" : "ACTIVE";
    }
}
