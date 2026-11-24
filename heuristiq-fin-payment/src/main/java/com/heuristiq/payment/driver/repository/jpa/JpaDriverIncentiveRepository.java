package com.heuristiq.paymentservice.driver.repository.jpa;

import com.heuristiq.paymentservice.driver.model.DriverIncentive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/** Spring Data JPA binding for {@code driver_incentives}. Not injected directly — see {@link DriverIncentiveRepositoryImpl}. */
interface JpaDriverIncentiveRepository extends JpaRepository<DriverIncentive, String> {

    List<DriverIncentive> findByDriverIdOrderByAwardedAtDesc(String driverId);

    List<DriverIncentive> findByDriverIdAndWeekStartDate(String driverId, LocalDate weekStartDate);
}
