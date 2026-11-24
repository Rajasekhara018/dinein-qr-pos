package com.heuristiq.payment.driver.repository.jpa;

import com.heuristiq.payment.driver.model.DriverPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA binding for {@code driver_payouts}. Not injected directly — see {@link DriverPayoutRepositoryImpl}. */
interface JpaDriverPayoutRepository extends JpaRepository<DriverPayout, String> {

    List<DriverPayout> findByDriverIdOrderByRequestedAtDesc(String driverId);
}
