package com.heuristq.dinein.table;

import com.heuristq.dinein.restaurant.domain.RestaurantEntity;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.table.domain.DiningTableEntity;
import com.heuristq.dinein.table.domain.DiningTableRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Same reasoning as {@code RestaurantOnboardingService}'s default table: the very first, self-hosted restaurant
 * (id {@link RestaurantEntity#DEFAULT_ID}, seeded outside the {@code /platform} onboarding flow) otherwise has no
 * table at all, so nobody can scan a QR to order until an owner logs in and creates one by hand. Skipped once any
 * table exists for that restaurant, and never runs for restaurants onboarded via {@code /platform} (those already
 * get their own T1 there) or when {@code app.seed.sample-data=true} (then {@code DevSampleDataRunner} seeds its own
 * T1/T2/COUNTER as part of the full sample restaurant, and would collide with this one on the unique label).
 */
@Slf4j
@Component
@Order(1)
public class BootstrapTableRunner implements ApplicationRunner {

    private final DiningTableRepository tableRepository;
    private final boolean sampleDataEnabled;

    public BootstrapTableRunner(DiningTableRepository tableRepository, AppProperties properties) {
        this.tableRepository = tableRepository;
        this.sampleDataEnabled = properties.seed().sampleData();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (sampleDataEnabled
                || !tableRepository.findAllByRestaurantIdOrderByLabelAsc(RestaurantEntity.DEFAULT_ID).isEmpty()) {
            return;
        }
        DiningTableEntity table = new DiningTableEntity();
        table.setRestaurantId(RestaurantEntity.DEFAULT_ID);
        table.setLabel("T1");
        table.setQrToken(TableService.newQrToken());
        tableRepository.save(table);
        log.info("bootstrap.table_created label=T1");
    }
}
