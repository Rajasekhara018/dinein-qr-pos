package com.heuristiq.paymentservice.driver.repository;

import com.heuristiq.paymentservice.driver.model.DriverPayout;

import java.util.List;

/**
 * Database-agnostic persistence port for {@code driver_payouts}. Implementations
 * (e.g. {@code jpa.DriverPayoutRepositoryImpl}) own the storage-specific details.
 */
public interface DriverPayoutRepository {

    DriverPayout save(DriverPayout payout);

    List<DriverPayout> findByDriverIdOrderByRequestedAtDesc(String driverId);
}
