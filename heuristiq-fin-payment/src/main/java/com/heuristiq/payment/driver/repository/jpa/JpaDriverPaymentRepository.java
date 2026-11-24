package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/** Spring Data JPA binding for {@code driver_payments}. Not injected directly — see {@link DriverPaymentRepositoryImpl}. */
interface JpaDriverPaymentRepository extends JpaRepository<DriverPayment, String> {

    List<DriverPayment> findByDriverIdOrderByCreatedAtDesc(String driverId);

    List<DriverPayment> findByDriverIdAndRideId(String driverId, String rideId);

    long countByDriverIdAndCreatedAtBetween(String driverId, LocalDateTime start, LocalDateTime end);
}
