package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverPayout;
import com.heuristiq.paymentservice.driver.repository.DriverPayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA/Postgres adapter for {@link DriverPayoutRepository}. */
@Repository
@RequiredArgsConstructor
class DriverPayoutRepositoryImpl implements DriverPayoutRepository {

    private final JpaDriverPayoutRepository jpaRepository;

    @Override
    public DriverPayout save(DriverPayout payout) {
        return jpaRepository.save(payout);
    }

    @Override
    public List<DriverPayout> findByDriverIdOrderByRequestedAtDesc(String driverId) {
        return jpaRepository.findByDriverIdOrderByRequestedAtDesc(driverId);
    }
}
