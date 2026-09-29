package com.heuristq.dinein.menu;

import com.heuristq.dinein.audit.AuditService;
import com.heuristq.dinein.image.ImageService;
import com.heuristq.dinein.image.ImageUrls;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.domain.KitchenStationEntity;
import com.heuristq.dinein.menu.domain.KitchenStationRepository;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.CategoryRequest;
import com.heuristq.dinein.menu.dto.MenuAdminDtos.CategoryResponse;
import com.heuristq.dinein.shared.exception.ApiException;
import com.heuristq.dinein.shared.security.CurrentStaff;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final KitchenStationRepository stationRepository;
    private final ImageService imageService;
    private final ApplicationEventPublisher events;
    private final AuditService auditService;

    public CategoryService(CategoryRepository categoryRepository, ItemRepository itemRepository,
                           KitchenStationRepository stationRepository, ImageService imageService,
                           ApplicationEventPublisher events, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.stationRepository = stationRepository;
        this.imageService = imageService;
        this.events = events;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        Long restaurantId = currentRestaurantId();
        Map<Long, Long> counts = itemRepository.countActiveByCategory(restaurantId).stream()
                .collect(Collectors.toMap(ItemRepository.CategoryCount::getCategoryId, ItemRepository.CategoryCount::getCount));
        List<CategoryEntity> categories = categoryRepository.findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(restaurantId);
        Map<Long, String> stationNames = stationNames(categories);
        return categories.stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L), stationNames.get(c.getStationId())))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Long restaurantId = currentRestaurantId();
        String name = request.name().trim();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, name)) {
            throw ApiException.conflict("DUPLICATE_NAME", "A category with this name already exists");
        }
        imageService.requireExists(request.imageId());
        KitchenStationEntity station = requireStation(request.stationId(), restaurantId);
        CategoryEntity category = new CategoryEntity();
        category.setRestaurantId(restaurantId);
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        category.setImageId(request.imageId());
        category.setActive(request.active() == null || request.active());
        category.setStationId(request.stationId());
        category.setDisplayOrder(categoryRepository.maxDisplayOrder(restaurantId) + 1);
        categoryRepository.save(category);
        log.info("category.created id={}", category.getId());
        events.publishEvent(new MenuChangedEvent("category.created"));
        return toResponse(category, 0, station == null ? null : station.getName());
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        CategoryEntity category = find(id);
        String name = request.name().trim();
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(category.getRestaurantId(), name, id)) {
            throw ApiException.conflict("DUPLICATE_NAME", "A category with this name already exists");
        }
        imageService.requireExists(request.imageId());
        KitchenStationEntity station = requireStation(request.stationId(), category.getRestaurantId());
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        category.setImageId(request.imageId());
        if (request.active() != null) {
            category.setActive(request.active());
        }
        category.setStationId(request.stationId());
        events.publishEvent(new MenuChangedEvent("category.updated"));
        return toResponse(category, countItems(id), station == null ? null : station.getName());
    }

    @Transactional
    public CategoryResponse setActive(Long id, boolean active) {
        CategoryEntity category = find(id);
        boolean previous = category.isActive();
        category.setActive(active);
        log.info("category.status id={} active={}", id, active);
        if (previous != active) {
            auditService.record("CATEGORY_STATUS_CHANGED", "Category", category.getId(),
                    String.valueOf(previous), String.valueOf(active));
        }
        events.publishEvent(new MenuChangedEvent("category.status"));
        return toResponse(category, countItems(id), stationName(category));
    }

    /** Applies the given order; ids not listed keep their relative order after the listed ones. */
    @Transactional
    public List<CategoryResponse> reorder(List<Long> orderedIds) {
        Set<Long> seen = new HashSet<>();
        for (Long id : orderedIds) {
            if (!seen.add(id)) {
                throw ApiException.badRequest("DUPLICATE_ID", "Category " + id + " is listed twice");
            }
        }
        Map<Long, CategoryEntity> byId = categoryRepository
                .findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(currentRestaurantId()).stream()
                .collect(Collectors.toMap(CategoryEntity::getId, Function.identity(), (a, b) -> a,
                        java.util.LinkedHashMap::new));
        int order = 1;
        for (Long id : orderedIds) {
            CategoryEntity category = byId.remove(id);
            if (category == null) {
                throw ApiException.badRequest("UNKNOWN_CATEGORY", "Category " + id + " not found");
            }
            category.setDisplayOrder(order++);
        }
        for (CategoryEntity rest : byId.values()) {
            rest.setDisplayOrder(order++);
        }
        events.publishEvent(new MenuChangedEvent("category.reordered"));
        return list();
    }

    CategoryEntity find(Long id) {
        return categoryRepository.findByIdAndRestaurantId(id, currentRestaurantId())
                .orElseThrow(() -> ApiException.notFound("Category"));
    }

    private long countItems(Long categoryId) {
        return itemRepository.countActiveByCategory(currentRestaurantId()).stream()
                .filter(c -> c.getCategoryId().equals(categoryId))
                .mapToLong(ItemRepository.CategoryCount::getCount)
                .findFirst().orElse(0);
    }

    private static Long currentRestaurantId() {
        return CurrentStaff.require().restaurantId();
    }

    /** Null is "unassigned"; otherwise the station must belong to this restaurant. */
    private KitchenStationEntity requireStation(Long stationId, Long restaurantId) {
        if (stationId == null) {
            return null;
        }
        return stationRepository.findByIdAndRestaurantId(stationId, restaurantId)
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_STATION", "Kitchen station not found"));
    }

    private String stationName(CategoryEntity category) {
        if (category.getStationId() == null) {
            return null;
        }
        return stationRepository.findByIdAndRestaurantId(category.getStationId(), category.getRestaurantId())
                .map(KitchenStationEntity::getName).orElse(null);
    }

    private Map<Long, String> stationNames(List<CategoryEntity> categories) {
        List<Long> ids = categories.stream().map(CategoryEntity::getStationId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            // Not Map.of(): callers look up by getStationId(), which is null for an unassigned category, and
            // Map.of()'s immutable map throws on get(null) instead of just returning null.
            return java.util.Collections.emptyMap();
        }
        return stationRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(KitchenStationEntity::getId, KitchenStationEntity::getName));
    }

    private CategoryResponse toResponse(CategoryEntity c, long itemCount, String stationName) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.getImageId(),
                ImageUrls.full(c.getImageId()), ImageUrls.thumb(c.getImageId()), c.getDisplayOrder(), c.isActive(),
                itemCount, c.getStationId(), stationName);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
