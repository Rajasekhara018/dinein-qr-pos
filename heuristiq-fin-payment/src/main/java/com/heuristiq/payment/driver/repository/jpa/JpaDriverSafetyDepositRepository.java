package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverSafetyDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA binding for {@code driver_safety_deposits}. Not injected directly — see {@link DriverSafetyDepositRepositoryImpl}. */
interface JpaDriverSafetyDepositRepository extends JpaRepository<DriverSafetyDeposit, String> {
}
