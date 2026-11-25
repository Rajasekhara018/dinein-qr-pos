package com.heuristiq.payment.splitfare.repository.jpa;

import com.heuristiq.payment.splitfare.model.SplitFareRequest;
import com.heuristiq.payment.splitfare.repository.SplitFareRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** JPA/Postgres adapter for {@link SplitFareRequestRepository}. */
@Repository
@RequiredArgsConstructor
class SplitFareRequestRepositoryImpl implements SplitFareRequestRepository {

    private final JpaSplitFareRequestRepository jpaRepository;

    @Override
    public SplitFareRequest save(SplitFareRequest splitFareRequest) {
        return jpaRepository.save(splitFareRequest);
    }

    @Override
    public Optional<SplitFareRequest> findById(String splitId) {
        return jpaRepository.findById(splitId);
    }

    @Override
    public Optional<SplitFareRequest> findByRideId(String rideId) {
        return jpaRepository.findByRideId(rideId);
    }
}
