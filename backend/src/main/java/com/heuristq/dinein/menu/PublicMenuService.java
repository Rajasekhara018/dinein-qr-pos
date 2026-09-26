package com.heuristq.dinein.menu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.image.ImageUrls;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuAddon;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuCategory;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuItem;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuResponse;
import com.heuristq.dinein.menu.dto.PublicMenuDtos.MenuVariant;
import com.heuristq.dinein.realtime.RealtimePublisher;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.shared.util.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Builds the guest menu and keeps it in memory. Any menu change publishes {@link MenuChangedEvent}; after the
 * transaction commits the cache is dropped and guests are told to refetch via {@code /topic/menu}.
 */
@Slf4j
@Service
public class PublicMenuService {

    public record CachedMenu(MenuResponse menu, String etag) {
    }

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final RestaurantSettingsRepository settingsRepository;
    private final RealtimePublisher realtimePublisher;
    private final ObjectMapper objectMapper;
    private final AtomicReference<CachedMenu> cache = new AtomicReference<>();
    /** Bumped on every invalidation so a build that started before a change is never cached. */
    private final AtomicLong generation = new AtomicLong();

    public PublicMenuService(CategoryRepository categoryRepository, ItemRepository itemRepository,
                             RestaurantSettingsRepository settingsRepository, RealtimePublisher realtimePublisher,
                             ObjectMapper objectMapper) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.settingsRepository = settingsRepository;
        this.realtimePublisher = realtimePublisher;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CachedMenu getMenu() {
        CachedMenu cached = cache.get();
        if (cached != null) {
            return cached;
        }
        long startedAt = generation.get();
        CachedMenu built = build();
        synchronized (cache) {
            if (generation.get() == startedAt) {
                cache.set(built);
            }
        }
        return built;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMenuChanged(MenuChangedEvent event) {
        synchronized (cache) {
            generation.incrementAndGet();
            cache.set(null);
        }
        log.debug("menu.cache.invalidated reason={}", event.reason());
        realtimePublisher.menuUpdated();
    }

    private CachedMenu build() {
        boolean pricesIncludeGst = settingsRepository.findById(RestaurantSettingsEntity.SINGLETON_ID)
                .map(RestaurantSettingsEntity::isPricesIncludeGst).orElse(false);
        List<CategoryEntity> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        Map<Long, List<ItemEntity>> itemsByCategory = categories.isEmpty() ? Map.of()
                : itemRepository.findByActiveTrueAndCategoryIdInOrderByDisplayOrderAscNameAsc(
                        categories.stream().map(CategoryEntity::getId).toList())
                .stream()
                .filter(this::isSellable)
                .collect(Collectors.groupingBy(ItemEntity::getCategoryId));
        List<MenuCategory> menuCategories = categories.stream()
                .map(c -> new MenuCategory(c.getId(), c.getName(), c.getDescription(),
                        ImageUrls.full(c.getImageId()), ImageUrls.thumb(c.getImageId()),
                        itemsByCategory.getOrDefault(c.getId(), List.of()).stream().map(this::toMenuItem).toList()))
                .filter(c -> !c.items().isEmpty())
                .toList();
        String version = versionOf(menuCategories, pricesIncludeGst);
        return new CachedMenu(new MenuResponse(version, pricesIncludeGst, menuCategories), "\"" + version + "\"");
    }

    /** Hides items that cannot be priced (defensive; the admin API already prevents this). */
    private boolean isSellable(ItemEntity item) {
        return item.hasActiveVariants() || (item.getBasePrice() != null && item.getBasePrice().signum() > 0);
    }

    private MenuItem toMenuItem(ItemEntity item) {
        return new MenuItem(item.getId(), item.getName(), item.getDescription(), item.getFoodType(),
                item.hasActiveVariants() ? null : item.getBasePrice(), ItemService.displayPrice(item),
                item.getGstPercent(), item.isAvailable(),
                ImageUrls.full(item.getImageId()), ImageUrls.thumb(item.getImageId()),
                item.activeVariants().stream()
                        .map(v -> new MenuVariant(v.getId(), v.getName(), v.getPrice(), v.isDefaultVariant())).toList(),
                item.activeAddons().stream().map(a -> new MenuAddon(a.getId(), a.getName(), a.getPrice())).toList());
    }

    private String versionOf(List<MenuCategory> categories, boolean pricesIncludeGst) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(new MenuResponse("", pricesIncludeGst, categories));
            return SecureTokens.sha256Hex(json).substring(0, 20);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
