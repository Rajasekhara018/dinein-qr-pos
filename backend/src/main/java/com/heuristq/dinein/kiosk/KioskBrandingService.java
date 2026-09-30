package com.heuristq.dinein.kiosk;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.image.ImageUrls;
import com.heuristq.dinein.kiosk.domain.KioskBrandingEntity;
import com.heuristq.dinein.kiosk.domain.KioskBrandingRepository;
import com.heuristq.dinein.kiosk.dto.KioskDtos.BrandingResponse;
import com.heuristq.dinein.kiosk.dto.KioskDtos.UpdateBrandingRequest;
import com.heuristq.dinein.settings.SettingsService;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import com.heuristq.dinein.shared.util.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Per-restaurant kiosk look and the kiosk kill switch. A restaurant with no saved branding gets app defaults. */
@Service
public class KioskBrandingService {

    /** The only method the kiosk offers today; UPI arrives with the payment-gateway work. */
    public static final String PAY_AT_COUNTER = "PAY_AT_COUNTER";

    private final KioskBrandingRepository repository;
    private final SettingsService settingsService;
    private final AuditService auditService;

    public KioskBrandingService(KioskBrandingRepository repository, SettingsService settingsService,
                                AuditService auditService) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BrandingResponse get(Long restaurantId) {
        String name = settingsService.forRestaurant(restaurantId).getName();
        return repository.findByRestaurantId(restaurantId)
                .map(b -> toResponse(name, b))
                .orElseGet(() -> new BrandingResponse(name, true, null, null, null, null, null, null, null, null,
                        List.of(PAY_AT_COUNTER), null, null));
    }

    @Transactional(readOnly = true)
    public boolean isKioskEnabled(Long restaurantId) {
        return repository.findByRestaurantId(restaurantId).map(KioskBrandingEntity::isKioskEnabled).orElse(true);
    }

    public void assertKioskEnabled(Long restaurantId) {
        if (!isKioskEnabled(restaurantId)) {
            throw ApiException.conflict("KIOSK_DISABLED", "Self-ordering is switched off. Please order at the counter.");
        }
    }

    @Transactional
    public BrandingResponse update(UpdateBrandingRequest request) {
        Long restaurantId = CurrentStaff.require().restaurantId();
        KioskBrandingEntity branding = repository.findByRestaurantId(restaurantId).orElseGet(() -> {
            KioskBrandingEntity created = new KioskBrandingEntity();
            created.setRestaurantId(restaurantId);
            return created;
        });
        boolean wasEnabled = branding.isKioskEnabled();
        branding.setKioskEnabled(request.kioskEnabled());
        branding.setPrimaryColor(request.primaryColor());
        branding.setSecondaryColor(request.secondaryColor());
        branding.setHeadline(blankToNull(Text.clean(request.headline(), 80)));
        branding.setSubtext(blankToNull(Text.clean(request.subtext(), 120)));
        branding.setStartButtonLabel(blankToNull(Text.clean(request.startButtonLabel(), 40)));
        branding.setIdleTimeoutSeconds(request.idleTimeoutSeconds());
        branding.setLogoImageId(request.logoImageId());
        branding.setBackgroundImageId(request.backgroundImageId());
        repository.save(branding);
        auditService.record(wasEnabled == branding.isKioskEnabled() ? "KIOSK_BRANDING_UPDATED"
                        : branding.isKioskEnabled() ? "KIOSK_ENABLED" : "KIOSK_DISABLED",
                "KioskBranding", branding.getId(), null, null);
        return toResponse(settingsService.forRestaurant(restaurantId).getName(), branding);
    }

    private static BrandingResponse toResponse(String restaurantName, KioskBrandingEntity b) {
        return new BrandingResponse(restaurantName, b.isKioskEnabled(), b.getPrimaryColor(), b.getSecondaryColor(),
                b.getHeadline(), b.getSubtext(), b.getStartButtonLabel(), b.getIdleTimeoutSeconds(),
                ImageUrls.full(b.getLogoImageId()), ImageUrls.full(b.getBackgroundImageId()),
                List.of(PAY_AT_COUNTER), b.getLogoImageId(), b.getBackgroundImageId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
