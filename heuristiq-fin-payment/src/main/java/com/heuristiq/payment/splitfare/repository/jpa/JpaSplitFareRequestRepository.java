package com.heuristiq.payment.splitfare.repository.jpa;

import com.heuristiq.payment.splitfare.model.SplitFareRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA binding for {@code split_fare_requests}. Not injected directly — see {@link SplitFareRequestRepositoryImpl}. */
interface JpaSplitFareRequestRepository extends JpaRepository<SplitFareRequest, String> {

    Optional<SplitFareRequest> findByRideId(String rideId);
}
