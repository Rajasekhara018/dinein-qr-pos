package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.repository.DriverSafetyDepositRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** JPA/Postgres adapter for {@link DriverSafetyDepositRepository}. */
@Repository
@RequiredArgsConstructor
class DriverSafetyDepositRepositoryImpl implements DriverSafetyDepositRepository {

    private final JpaDriverSafetyDepositRepository jpaRepository;
}
