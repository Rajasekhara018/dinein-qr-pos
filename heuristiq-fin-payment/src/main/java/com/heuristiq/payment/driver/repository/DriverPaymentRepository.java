package com.heuristiq.paymentservice.driver.repository;

import com.heuristiq.paymentservice.driver.model.DriverPayment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Database-agnostic persistence port for {@code driver_payments}. Implementations
 * (e.g. {@code jpa.DriverPaymentRepositoryImpl}) own the storage-specific details. Read-only: Payment no longer writes driver
 * payment records (drivers' earnings are booked in Rider_WalletService); the rows stay readable as history.
 */
public interface DriverPaymentRepository {

    List<DriverPayment> findByDriverIdOrderByCreatedAtDesc(String driverId);

    List<DriverPayment> findByDriverIdAndRideId(String driverId, String rideId);

    /** DriverIncentiveService's TRIP_COUNT rule: real weekly trip count for a driver. */
    long countByDriverIdAndCreatedAtBetween(String driverId, LocalDateTime start, LocalDateTime end);
}
