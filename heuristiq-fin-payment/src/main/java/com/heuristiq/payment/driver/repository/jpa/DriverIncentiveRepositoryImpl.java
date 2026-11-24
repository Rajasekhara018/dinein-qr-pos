package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverIncentive;
import com.heuristiq.paymentservice.driver.repository.DriverIncentiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/** JPA/Postgres adapter for {@link DriverIncentiveRepository}. */
@Repository
@RequiredArgsConstructor
class DriverIncentiveRepositoryImpl implements DriverIncentiveRepository {

    private final JpaDriverIncentiveRepository jpaRepository;

    @Override
    public DriverIncentive save(DriverIncentive incentive) {
        return jpaRepository.save(incentive);
    }

    @Override
    public List<DriverIncentive> findByDriverIdOrderByAwardedAtDesc(String driverId) {
        return jpaRepository.findByDriverIdOrderByAwardedAtDesc(driverId);
    }

    @Override
    public List<DriverIncentive> findByDriverIdAndWeekStartDate(String driverId, LocalDate weekStartDate) {
        return jpaRepository.findByDriverIdAndWeekStartDate(driverId, weekStartDate);
    }
}
