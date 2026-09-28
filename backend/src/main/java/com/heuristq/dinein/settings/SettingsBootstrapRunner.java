package com.heuristq.dinein.settings;

import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsEntity;
import com.heuristq.dinein.settings.domain.RestaurantSettingsRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Guarantees the single {@code restaurant_settings} row exists (every profile). The owner edits it in the admin UI. */
@Slf4j
@Component
@Order(0)
public class SettingsBootstrapRunner implements ApplicationRunner {

    private final RestaurantSettingsRepository repository;

    public SettingsBootstrapRunner(RestaurantSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.existsByRestaurantId(RestaurantEntity.DEFAULT_ID)) {
            return;
        }
        RestaurantSettingsEntity settings = new RestaurantSettingsEntity();
        settings.setRestaurantId(RestaurantEntity.DEFAULT_ID);
        settings.setName("My Restaurant");
        repository.save(settings);
        log.info("bootstrap.settings_created");
    }
}
