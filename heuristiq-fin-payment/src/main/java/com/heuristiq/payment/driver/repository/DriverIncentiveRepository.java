package com.heuristiq.paymentservice.driver.repository;

import com.heuristiq.paymentservice.driver.model.DriverIncentive;

import java.time.LocalDate;
import java.util.List;

/**
 * Database-agnostic persistence port for {@code driver_incentives}. Implementations
 * (e.g. {@code jpa.DriverIncentiveRepositoryImpl}) own the storage-specific details.
 */
public interface DriverIncentiveRepository {

    DriverIncentive save(DriverIncentive incentive);

    List<DriverIncentive> findByDriverIdOrderByAwardedAtDesc(String driverId);

    List<DriverIncentive> findByDriverIdAndWeekStartDate(String driverId, LocalDate weekStartDate);
}
