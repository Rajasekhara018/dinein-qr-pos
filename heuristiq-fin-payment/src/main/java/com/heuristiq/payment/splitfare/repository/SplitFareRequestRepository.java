package com.heuristiq.paymentservice.splitfare.repository;

import com.heuristiq.paymentservice.splitfare.model.SplitFareRequest;

import java.util.Optional;

/**
 * Database-agnostic persistence port for {@code split_fare_requests}. Implementations
 * (e.g. {@code jpa.SplitFareRequestRepositoryImpl}) own the storage-specific details.
 */
public interface SplitFareRequestRepository {

    SplitFareRequest save(SplitFareRequest splitFareRequest);

    Optional<SplitFareRequest> findById(String splitId);

    Optional<SplitFareRequest> findByRideId(String rideId);
}
