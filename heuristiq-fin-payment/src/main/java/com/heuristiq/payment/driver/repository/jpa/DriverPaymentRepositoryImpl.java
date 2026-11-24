package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverPayment;
import com.heuristiq.paymentservice.driver.repository.DriverPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** JPA/Postgres adapter for {@link DriverPaymentRepository}. */
@Repository
@RequiredArgsConstructor
class DriverPaymentRepositoryImpl implements DriverPaymentRepository {

    private final JpaDriverPaymentRepository jpaRepository;

    @Override
    public List<DriverPayment> findByDriverIdOrderByCreatedAtDesc(String driverId) {
        return jpaRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
    }

    @Override
    public List<DriverPayment> findByDriverIdAndRideId(String driverId, String rideId) {
        return jpaRepository.findByDriverIdAndRideId(driverId, rideId);
    }

    @Override
    public long countByDriverIdAndCreatedAtBetween(String driverId, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByDriverIdAndCreatedAtBetween(driverId, start, end);
    }
}
