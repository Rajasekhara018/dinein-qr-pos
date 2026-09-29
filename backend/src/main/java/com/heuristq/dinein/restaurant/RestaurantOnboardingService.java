package com.heuristq.dinein.restaurant;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.auth.PasswordPolicy;
import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.restaurant.domain.RestaurantRepository;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantRequest;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OnboardRestaurantResponse;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.OwnerSummary;
import com.heuristq.dinein.restaurant.dto.OnboardingDtos.RestaurantSummary;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.SecureTokens;
import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;
import com.heuristq.dinein.staff.domain.StaffUserRepository;
import com.heuristq.dinein.table.TableService;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Creates a new tenant end to end: the {@link RestaurantEntity} row, its settings, and its first OWNER account.
 * Not merchant self-serve: called by whoever operates the platform (see {@code PlatformOnboardingController}) after
 * onboarding a restaurant offline. The owner then signs in with the returned temporary password and is forced to
 * change it, same as {@code BootstrapOwnerRunner}'s first-ever owner.
 */
@Slf4j
@Service
public class RestaurantOnboardingService {

    private final RestaurantRepository restaurantRepository;
    private final RestaurantSettingsRepository settingsRepository;
    private final StaffUserRepository staffUserRepository;
    private final DiningTableRepository tableRepository;
    private final TableService tableService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public RestaurantOnboardingService(RestaurantRepository restaurantRepository,
                                       RestaurantSettingsRepository settingsRepository,
                                       StaffUserRepository staffUserRepository, DiningTableRepository tableRepository,
                                       TableService tableService, PasswordEncoder passwordEncoder,
                                       AuditService auditService) {
        this.restaurantRepository = restaurantRepository;
        this.settingsRepository = settingsRepository;
        this.staffUserRepository = staffUserRepository;
        this.tableRepository = tableRepository;
        this.tableService = tableService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public OnboardRestaurantResponse onboard(OnboardRestaurantRequest request) {
        String name = request.restaurantName().trim();
        String slug = normalizeSlug(request.slug() != null && !request.slug().isBlank() ? request.slug() : name);
        if (restaurantRepository.findBySlugIgnoreCase(slug).isPresent()) {
            throw ApiException.conflict("DUPLICATE_SLUG", "A restaurant with this slug already exists");
        }
        // Login is not yet restaurant-aware, so the username itself must be globally unique. Defaulting to the
        // slug prefix guarantees that without asking the operator to pick one, but they can still override it
        // (e.g. to match a name the merchant already expects) as long as it's not already taken.
        String username = request.ownerUsername() != null && !request.ownerUsername().isBlank()
                ? request.ownerUsername().trim() : slug + ".owner";
        if (staffUserRepository.existsByUsernameIgnoreCase(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "Username is already in use");
        }
        String temporaryPassword;
        if (request.ownerPassword() != null && !request.ownerPassword().isBlank()) {
            PasswordPolicy.validate(request.ownerPassword());
            temporaryPassword = request.ownerPassword();
        } else {
            temporaryPassword = SecureTokens.randomUrlSafe(9);
        }

        RestaurantEntity restaurant = new RestaurantEntity();
        restaurant.setName(name);
        restaurant.setSlug(slug);
        restaurantRepository.save(restaurant);

        RestaurantSettingsEntity settings = new RestaurantSettingsEntity();
        settings.setRestaurantId(restaurant.getId());
        settings.setName(name);
        applyIfPresent(request.address(), settings::setAddress);
        applyIfPresent(request.phone(), settings::setPhone);
        applyIfPresent(request.gstin(), v -> settings.setGstin(v.toUpperCase(Locale.ROOT)));
        applyIfPresent(request.fssaiNo(), settings::setFssaiNo);
        applyIfPresent(request.brandColor(), settings::setBrandColor);
        if (request.pricesIncludeGst() != null) settings.setPricesIncludeGst(request.pricesIncludeGst());
        if (request.takeawayEnabled() != null) settings.setTakeawayEnabled(request.takeawayEnabled());
        if (request.openingTime() != null) settings.setOpeningTime(request.openingTime());
        if (request.closingTime() != null) settings.setClosingTime(request.closingTime());
        settingsRepository.save(settings);

        StaffUserEntity owner = new StaffUserEntity();
        owner.setRestaurantId(restaurant.getId());
        owner.setUsername(username);
        owner.setDisplayName(request.ownerDisplayName() == null || request.ownerDisplayName().isBlank()
                ? "Owner" : request.ownerDisplayName().trim());
        owner.setRole(StaffRole.OWNER);
        applyIfPresent(request.ownerEmail(), v -> owner.setEmail(v.toLowerCase(Locale.ROOT)));
        applyIfPresent(request.ownerPhone(), owner::setPhone);
        owner.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        owner.setMustChangePassword(true);
        staffUserRepository.save(owner);

        // Without at least one table, a freshly onboarded restaurant can't receive a single QR order until someone
        // logs in and creates one by hand. T1 gets them a working QR immediately; they can rename/add more later.
        DiningTableEntity table = new DiningTableEntity();
        table.setRestaurantId(restaurant.getId());
        table.setLabel("T1");
        table.setQrToken(TableService.newQrToken());
        tableRepository.save(table);

        log.info("restaurant.onboarded id={} slug={} ownerUsername={}", restaurant.getId(), slug, username);
        auditService.recordForRestaurant(restaurant.getId(), "RESTAURANT_ONBOARDED", "Restaurant", restaurant.getId(),
                null, slug);
        return new OnboardRestaurantResponse(restaurant.getId(), name, slug, username, temporaryPassword,
                tableService.menuUrl(table));
    }

    /** Runs `setter` only when `value` is non-blank, leaving the entity's own default otherwise. */
    private static void applyIfPresent(String value, Consumer<String> setter) {
        if (value != null && !value.isBlank()) setter.accept(value.trim());
    }

    /** Every restaurant on the platform with its owner accounts, for the platform operator's own view. */
    @Transactional(readOnly = true)
    public List<RestaurantSummary> listRestaurants() {
        Map<Long, List<StaffUserEntity>> ownersByRestaurant = staffUserRepository
                .findByRoleOrderByUsernameAsc(StaffRole.OWNER).stream()
                .collect(Collectors.groupingBy(StaffUserEntity::getRestaurantId));
        return restaurantRepository.findAll().stream()
                .map(r -> new RestaurantSummary(r.getId(), r.getName(), r.getSlug(), r.getStatus(), r.getCreatedAt(),
                        ownersByRestaurant.getOrDefault(r.getId(), List.of()).stream()
                                .map(o -> new OwnerSummary(o.getId(), o.getUsername(), o.getDisplayName(), o.isActive()))
                                .toList()))
                .toList();
    }

    private static String normalizeSlug(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            throw ApiException.badRequest("INVALID_SLUG", "Provide a valid restaurant name or slug");
        }
        return slug;
    }
}
