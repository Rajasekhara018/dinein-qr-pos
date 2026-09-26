package com.heuristq.dinein.settings;

import com.heuristq.dinein.image.ImageUrls;
import com.heuristq.dinein.image.ImageService;
import com.heuristq.dinein.menu.MenuChangedEvent;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import com.heuristq.dinein.settings.dto.SettingsDtos.PublicRestaurantInfo;
import com.heuristq.dinein.settings.dto.SettingsDtos.SettingsResponse;
import com.heuristq.dinein.settings.dto.SettingsDtos.UpdateSettingsRequest;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.BusinessTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
public class SettingsService {

    private final RestaurantSettingsRepository repository;
    private final ImageService imageService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SettingsService(RestaurantSettingsRepository repository, ImageService imageService,
                           ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.imageService = imageService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RestaurantSettingsEntity current() {
        return repository.findById(RestaurantSettingsEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("restaurant_settings row is missing"));
    }

    @Transactional(readOnly = true)
    public SettingsResponse get() {
        return toResponse(current());
    }

    @Transactional
    public SettingsResponse update(UpdateSettingsRequest r) {
        if (r.logoImageId() != null) {
            imageService.requireExists(r.logoImageId());
        }
        if (r.kitchenAlertMinutes() <= r.kitchenWarnMinutes()) {
            throw ApiException.badRequest("INVALID_THRESHOLDS", "Alert threshold must be greater than warning threshold");
        }
        RestaurantSettingsEntity s = current();
        s.setName(r.name().trim());
        s.setAddress(blankToNull(r.address()));
        s.setPhone(blankToNull(r.phone()));
        s.setGstin(blankToNull(r.gstin()));
        s.setFssaiNo(blankToNull(r.fssaiNo()));
        s.setLogoImageId(r.logoImageId());
        s.setAcceptingOrders(r.acceptingOrders());
        s.setPricesIncludeGst(r.pricesIncludeGst());
        s.setOpeningTime(r.openingTime());
        s.setClosingTime(r.closingTime());
        if (r.brandColor() != null) {
            s.setBrandColor(r.brandColor().toUpperCase());
        }
        s.setKitchenWarnMinutes(r.kitchenWarnMinutes());
        s.setKitchenAlertMinutes(r.kitchenAlertMinutes());
        s.setReadyAutoHideMinutes(r.readyAutoHideMinutes());
        repository.save(s);
        log.info("settings.updated acceptingOrders={} pricesIncludeGst={}", s.isAcceptingOrders(), s.isPricesIncludeGst());
        // Pricing mode and open/closed state are part of what guests see.
        events.publishEvent(new MenuChangedEvent("settings"));
        return toResponse(s);
    }

    @Transactional(readOnly = true)
    public PublicRestaurantInfo publicInfo() {
        RestaurantSettingsEntity s = current();
        return new PublicRestaurantInfo(s.getName(), s.getAddress(), s.getPhone(), s.getGstin(), s.getFssaiNo(),
                ImageUrls.full(s.getLogoImageId()), s.getBrandColor(), s.isAcceptingOrders(), isOpenNow(s),
                s.getOpeningTime(), s.getClosingTime(), s.isPricesIncludeGst());
    }

    public boolean isOpenNow(RestaurantSettingsEntity s) {
        return BusinessTime.isWithin(s.getOpeningTime(), s.getClosingTime(), BusinessTime.nowTime(clock));
    }

    /** Throws when guests cannot order right now (switched off or outside opening hours). */
    public void assertAcceptingOrders(RestaurantSettingsEntity s) {
        if (!s.isAcceptingOrders()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORDERING_CLOSED",
                    "We are not accepting orders right now");
        }
        if (!isOpenNow(s)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OUTSIDE_OPENING_HOURS",
                    "We are closed right now. Opening hours: " + s.getOpeningTime() + " - " + s.getClosingTime());
        }
    }

    private SettingsResponse toResponse(RestaurantSettingsEntity s) {
        return new SettingsResponse(s.getName(), s.getAddress(), s.getPhone(), s.getGstin(), s.getFssaiNo(),
                s.getLogoImageId(), ImageUrls.full(s.getLogoImageId()), s.isAcceptingOrders(), s.isPricesIncludeGst(),
                s.getOpeningTime(), s.getClosingTime(), s.getCurrency(), s.getBrandColor(),
                s.getKitchenWarnMinutes(), s.getKitchenAlertMinutes(), s.getReadyAutoHideMinutes());
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
