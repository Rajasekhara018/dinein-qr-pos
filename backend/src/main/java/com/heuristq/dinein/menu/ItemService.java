package com.heuristq.dinein.menu;

import com.heuristq.dinein.image.ImageService;
import com.heuristq.dinein.image.ImageUrls;
import com.heuristq.dinein.menu.domain.AddonEntity;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.domain.ItemVariantEntity;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.AddonRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.AddonResponse;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.ItemRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.ItemResponse;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.PriceRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.VariantPrice;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.VariantRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.VariantResponse;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.util.Money;
import com.heuristq.dinein.shared.web.PageResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher events;

    public ItemService(ItemRepository itemRepository, CategoryRepository categoryRepository,
                       ImageService imageService, ApplicationEventPublisher events) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.imageService = imageService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public PageResponse<ItemResponse> search(Long categoryId, String q, Boolean available, int page, int size) {
        Specification<ItemEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (available != null) {
                predicates.add(cb.equal(root.get("available"), available));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), like, '\\'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ItemEntity> result = itemRepository.findAll(spec, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), Sort.by("categoryId", "displayOrder", "name")));
        Map<Long, String> categoryNames = categoryNames();
        return PageResponse.of(result, item -> toResponse(item, categoryNames.get(item.getCategoryId())));
    }

    @Transactional(readOnly = true)
    public ItemResponse get(Long id) {
        ItemEntity item = find(id);
        return toResponse(item, categoryName(item.getCategoryId()));
    }

    @Transactional
    public ItemResponse create(ItemRequest request) {
        CategoryEntity category = requireCategory(request.categoryId());
        String name = request.name().trim();
        if (itemRepository.existsByCategoryIdAndNameIgnoreCase(category.getId(), name)) {
            throw ApiException.conflict("DUPLICATE_NAME", "An item with this name already exists in this category");
        }
        imageService.requireExists(request.imageId());
        ItemEntity item = new ItemEntity();
        item.setDisplayOrder(itemRepository.maxDisplayOrder(category.getId()) + 1);
        applyFields(item, request, name);
        syncVariants(item, request.variants());
        syncAddons(item, request.addons());
        validatePricing(item);
        itemRepository.saveAndFlush(item);
        log.info("item.created id={} categoryId={}", item.getId(), item.getCategoryId());
        events.publishEvent(new MenuChangedEvent("item.created"));
        return toResponse(item, category.getName());
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest request) {
        ItemEntity item = find(id);
        checkVersion(item, request.version());
        CategoryEntity category = requireCategory(request.categoryId());
        String name = request.name().trim();
        if (itemRepository.existsByCategoryIdAndNameIgnoreCaseAndIdNot(category.getId(), name, id)) {
            throw ApiException.conflict("DUPLICATE_NAME", "An item with this name already exists in this category");
        }
        imageService.requireExists(request.imageId());
        applyFields(item, request, name);
        syncVariants(item, request.variants());
        syncAddons(item, request.addons());
        validatePricing(item);
        touch(item);
        log.info("item.updated id={}", id);
        events.publishEvent(new MenuChangedEvent("item.updated"));
        return toResponse(item, category.getName());
    }

    @Transactional
    public ItemResponse setAvailability(Long id, boolean available) {
        ItemEntity item = find(id);
        item.setAvailable(available);
        itemRepository.saveAndFlush(item);
        log.info("item.availability id={} available={}", id, available);
        events.publishEvent(new MenuChangedEvent("item.availability"));
        return toResponse(item, categoryName(item.getCategoryId()));
    }

    @Transactional
    public ItemResponse updatePrice(Long id, PriceRequest request) {
        ItemEntity item = find(id);
        checkVersion(item, request.version());
        boolean changed = false;
        if (request.basePrice() != null) {
            if (item.hasActiveVariants()) {
                throw ApiException.badRequest("HAS_VARIANTS", "This item is priced by size; edit the size prices instead");
            }
            item.setBasePrice(Money.round(request.basePrice()));
            changed = true;
        }
        if (request.variants() != null && !request.variants().isEmpty()) {
            Map<Long, ItemVariantEntity> variants = item.getVariants().stream()
                    .filter(ItemVariantEntity::isActive)
                    .collect(Collectors.toMap(ItemVariantEntity::getId, Function.identity()));
            for (VariantPrice vp : request.variants()) {
                ItemVariantEntity variant = variants.get(vp.id());
                if (variant == null) {
                    throw ApiException.badRequest("UNKNOWN_VARIANT", "Size " + vp.id() + " does not belong to this item");
                }
                variant.setPrice(Money.round(vp.price()));
            }
            changed = true;
        }
        if (!changed) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Provide a base price or size prices");
        }
        touch(item);
        log.info("item.price_updated id={}", id);
        events.publishEvent(new MenuChangedEvent("item.price"));
        return toResponse(item, categoryName(item.getCategoryId()));
    }

    /** Soft delete: past orders keep referencing the row. */
    @Transactional
    public void delete(Long id) {
        ItemEntity item = find(id);
        item.setActive(false);
        log.info("item.deleted id={}", id);
        events.publishEvent(new MenuChangedEvent("item.deleted"));
    }

    private void applyFields(ItemEntity item, ItemRequest r, String name) {
        item.setCategoryId(r.categoryId());
        item.setName(name);
        item.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        item.setImageId(r.imageId());
        item.setFoodType(r.foodType());
        item.setGstPercent(r.gstPercent().setScale(2, java.math.RoundingMode.HALF_UP));
        if (r.available() != null) {
            item.setAvailable(r.available());
        }
        boolean hasVariants = r.variants() != null && !r.variants().isEmpty();
        item.setBasePrice(hasVariants || r.basePrice() == null ? null : Money.round(r.basePrice()));
    }

    /** Updates variants in place by id, adds new ones and deactivates removed ones (they may be on past orders). */
    private void syncVariants(ItemEntity item, List<VariantRequest> requested) {
        List<VariantRequest> incoming = requested == null ? List.of() : requested;
        assertUniqueNames(incoming.stream().map(VariantRequest::name).toList(), "size");
        Map<Long, ItemVariantEntity> existing = new HashMap<>();
        item.getVariants().forEach(v -> existing.put(v.getId(), v));
        Set<Long> kept = new HashSet<>();
        boolean defaultAssigned = false;
        long defaultsRequested = incoming.stream().filter(VariantRequest::isDefault).count();
        for (int i = 0; i < incoming.size(); i++) {
            VariantRequest vr = incoming.get(i);
            ItemVariantEntity variant;
            if (vr.id() != null) {
                variant = existing.get(vr.id());
                if (variant == null) {
                    throw ApiException.badRequest("UNKNOWN_VARIANT", "Size " + vr.id() + " does not belong to this item");
                }
                kept.add(vr.id());
            } else {
                variant = new ItemVariantEntity();
                variant.setItem(item);
                item.getVariants().add(variant);
            }
            variant.setName(vr.name().trim());
            variant.setPrice(Money.round(vr.price()));
            variant.setDisplayOrder(i);
            variant.setActive(true);
            boolean makeDefault = !defaultAssigned && (vr.isDefault() || (defaultsRequested == 0 && i == 0));
            variant.setDefaultVariant(makeDefault);
            defaultAssigned |= makeDefault;
        }
        existing.values().stream()
                .filter(v -> !kept.contains(v.getId()))
                .forEach(v -> {
                    v.setActive(false);
                    v.setDefaultVariant(false);
                });
    }

    private void syncAddons(ItemEntity item, List<AddonRequest> requested) {
        List<AddonRequest> incoming = requested == null ? List.of() : requested;
        assertUniqueNames(incoming.stream().map(AddonRequest::name).toList(), "add-on");
        Map<Long, AddonEntity> existing = new HashMap<>();
        item.getAddons().forEach(a -> existing.put(a.getId(), a));
        Set<Long> kept = new HashSet<>();
        for (AddonRequest ar : incoming) {
            AddonEntity addon;
            if (ar.id() != null) {
                addon = existing.get(ar.id());
                if (addon == null) {
                    throw ApiException.badRequest("UNKNOWN_ADDON", "Add-on " + ar.id() + " does not belong to this item");
                }
                kept.add(ar.id());
            } else {
                addon = new AddonEntity();
                addon.setItem(item);
                item.getAddons().add(addon);
            }
            addon.setName(ar.name().trim());
            addon.setPrice(Money.round(ar.price()));
            addon.setActive(true);
        }
        existing.values().stream().filter(a -> !kept.contains(a.getId())).forEach(a -> a.setActive(false));
    }

    private void validatePricing(ItemEntity item) {
        boolean hasBase = item.getBasePrice() != null && item.getBasePrice().signum() > 0;
        if (!hasBase && !item.hasActiveVariants()) {
            throw ApiException.badRequest("PRICE_REQUIRED", "Set a price greater than zero, or add at least one size");
        }
    }

    private void checkVersion(ItemEntity item, Long expected) {
        if (expected != null && expected != item.getVersion()) {
            throw new OptimisticLockingFailureException("item " + item.getId() + " version mismatch");
        }
    }

    /**
     * Child-only edits do not dirty the item row; bump it so @Version detects concurrent edits, and flush so the
     * response carries the new version for the client's next edit.
     */
    private void touch(ItemEntity item) {
        item.setUpdatedAt(java.time.Instant.now());
        itemRepository.saveAndFlush(item);
    }

    private static void assertUniqueNames(List<String> names, String label) {
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            if (!seen.add(n.trim().toLowerCase(Locale.ROOT))) {
                throw ApiException.badRequest("DUPLICATE_" + label.toUpperCase(Locale.ROOT).replace("-", ""),
                        "Duplicate " + label + " name: " + n.trim());
            }
        }
    }

    private CategoryEntity requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_CATEGORY", "Category not found"));
    }

    private ItemEntity find(Long id) {
        return itemRepository.findById(id).filter(ItemEntity::isActive)
                .orElseThrow(() -> ApiException.notFound("Item"));
    }

    private Map<Long, String> categoryNames() {
        return categoryRepository.findAll().stream()
                .collect(Collectors.toMap(CategoryEntity::getId, CategoryEntity::getName));
    }

    private String categoryName(Long id) {
        return categoryRepository.findById(id).map(CategoryEntity::getName).orElse(null);
    }

    static BigDecimal displayPrice(ItemEntity item) {
        return item.activeVariants().stream()
                .map(ItemVariantEntity::getPrice)
                .min(Comparator.naturalOrder())
                .orElse(item.getBasePrice());
    }

    private ItemResponse toResponse(ItemEntity item, String categoryName) {
        List<VariantResponse> variants = item.activeVariants().stream()
                .map(v -> new VariantResponse(v.getId(), v.getName(), v.getPrice(), v.isDefaultVariant()))
                .toList();
        List<AddonResponse> addons = item.activeAddons().stream()
                .map(a -> new AddonResponse(a.getId(), a.getName(), a.getPrice()))
                .toList();
        return new ItemResponse(item.getId(), item.getCategoryId(), categoryName, item.getName(), item.getDescription(),
                item.getImageId(), ImageUrls.full(item.getImageId()), ImageUrls.thumb(item.getImageId()),
                item.getBasePrice(), item.getFoodType(), item.getGstPercent(), item.isAvailable(), item.isActive(),
                item.getDisplayOrder(), item.getVersion(), displayPrice(item), !variants.isEmpty(), variants, addons);
    }
}
