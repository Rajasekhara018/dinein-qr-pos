package com.heuristq.dinein.settings;

import com.heuristq.dinein.menu.domain.AddonEntity;
import com.heuristq.dinein.menu.domain.CategoryEntity;
import com.heuristq.dinein.menu.domain.CategoryRepository;
import com.heuristq.dinein.menu.domain.FoodType;
import com.heuristq.dinein.menu.domain.ItemEntity;
import com.heuristq.dinein.menu.domain.ItemRepository;
import com.heuristq.dinein.menu.domain.ItemVariantEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import com.heuristq.dinein.table.TableService;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Sample restaurant for local development ({@code app.seed.sample-data=true}, on in the dev profile): 3 tables,
 * 3 categories and 8 items, some with sizes and add-ons. Runs only on an empty menu, so it never touches real data.
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.sample-data", havingValue = "true")
public class DevSampleDataRunner implements ApplicationRunner {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final DiningTableRepository tableRepository;
    private final RestaurantSettingsRepository settingsRepository;

    public DevSampleDataRunner(CategoryRepository categoryRepository, ItemRepository itemRepository,
                               DiningTableRepository tableRepository, RestaurantSettingsRepository settingsRepository) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.tableRepository = tableRepository;
        this.settingsRepository = settingsRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryRepository.count() > 0) {
            return;
        }
        settingsRepository.findById(RestaurantSettingsEntity.SINGLETON_ID).ifPresent(s -> {
            s.setName("Spice Route Kitchen");
            s.setAddress("12 MG Road, Bengaluru 560001");
            s.setPhone("9876543210");
            s.setGstin("29ABCDE1234F1Z5");
            s.setFssaiNo("11223344556677");
            s.setOpeningTime(LocalTime.of(0, 0));
            s.setClosingTime(LocalTime.of(0, 0));
        });

        for (String label : List.of("T1", "T2", "COUNTER")) {
            DiningTableEntity table = new DiningTableEntity();
            table.setLabel(label);
            table.setQrToken(TableService.newQrToken());
            tableRepository.save(table);
        }

        CategoryEntity starters = category("Starters", "Small plates to share", 1);
        CategoryEntity mains = category("Mains", "Served with salad", 2);
        CategoryEntity drinks = category("Drinks", "Chilled and hot beverages", 3);

        item(starters, "Paneer Tikka", "Chargrilled cottage cheese, mint chutney", FoodType.VEG, "240.00", 1,
                List.of(), List.of(addon("Extra chutney", "20.00")));
        item(starters, "Chicken 65", "Spicy fried chicken, curry leaves", FoodType.NON_VEG, null, 2,
                List.of(variant("Half", "180.00", true), variant("Full", "320.00", false)), List.of());
        item(starters, "Masala Omelette", "Three eggs, onion, green chilli", FoodType.EGG, "120.00", 3,
                List.of(), List.of(addon("Cheese", "30.00")));
        item(mains, "Veg Biryani", "Aromatic basmati, seasonal vegetables, raita", FoodType.VEG, null, 1,
                List.of(variant("Half", "160.00", false), variant("Full", "260.00", true)),
                List.of(addon("Extra raita", "25.00"), addon("Boiled egg", "20.00")));
        item(mains, "Butter Chicken", "Creamy tomato gravy", FoodType.NON_VEG, "340.00", 2,
                List.of(), List.of(addon("Butter naan", "45.00"), addon("Jeera rice", "90.00")));
        item(mains, "Dal Tadka", "Yellow lentils tempered with ghee", FoodType.VEG, "180.00", 3, List.of(), List.of());
        item(drinks, "Masala Chai", "Ginger & cardamom", FoodType.VEG, "40.00", 1, List.of(), List.of());
        item(drinks, "Fresh Lime Soda", "Sweet or salted", FoodType.VEG, null, 2,
                List.of(variant("300 ml", "70.00", true), variant("500 ml", "100.00", false)), List.of());

        log.info("seed.sample_data_created tables=3 categories=3 items=8");
    }

    private CategoryEntity category(String name, String description, int order) {
        CategoryEntity c = new CategoryEntity();
        c.setName(name);
        c.setDescription(description);
        c.setDisplayOrder(order);
        return categoryRepository.save(c);
    }

    private void item(CategoryEntity category, String name, String description, FoodType type, String basePrice,
                      int order, List<ItemVariantEntity> variants, List<AddonEntity> addons) {
        ItemEntity item = new ItemEntity();
        item.setCategoryId(category.getId());
        item.setName(name);
        item.setDescription(description);
        item.setFoodType(type);
        item.setBasePrice(basePrice == null ? null : new BigDecimal(basePrice));
        item.setDisplayOrder(order);
        for (int i = 0; i < variants.size(); i++) {
            ItemVariantEntity v = variants.get(i);
            v.setItem(item);
            v.setDisplayOrder(i);
            item.getVariants().add(v);
        }
        addons.forEach(a -> {
            a.setItem(item);
            item.getAddons().add(a);
        });
        itemRepository.save(item);
    }

    private static ItemVariantEntity variant(String name, String price, boolean isDefault) {
        ItemVariantEntity v = new ItemVariantEntity();
        v.setName(name);
        v.setPrice(new BigDecimal(price));
        v.setDefaultVariant(isDefault);
        return v;
    }

    private static AddonEntity addon(String name, String price) {
        AddonEntity a = new AddonEntity();
        a.setName(name);
        a.setPrice(new BigDecimal(price));
        return a;
    }
}
