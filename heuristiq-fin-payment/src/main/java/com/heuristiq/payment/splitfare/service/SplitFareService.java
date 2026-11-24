package com.heuristiq.paymentservice.splitfare.service;

import com.heuristiq.phone.PhoneNumbers;
import com.heuristiq.paymentservice.security.TenantAccess;
import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.client.dto.RideDetailsResponse;
import com.heuristiq.paymentservice.config.MoneyProperties;
import com.heuristiq.paymentservice.shared.money.AmountCurrency;
import com.heuristiq.paymentservice.security.OwnershipGuard;
import com.heuristiq.paymentservice.shared.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import com.heuristiq.paymentservice.splitfare.dto.CreateSplitFareRequest;
import com.heuristiq.paymentservice.splitfare.model.SplitFareRequest;
import com.heuristiq.paymentservice.splitfare.model.SplitParticipant;
import com.heuristiq.paymentservice.splitfare.repository.SplitFareRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ported from the monolith's splitfare.SplitFareService — fetches the ride's fare via RideServiceClient instead of a local RideRequest repository. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SplitFareService {

    private final SplitFareRequestRepository splitFareRepository;
    private final RideServiceClient rideServiceClient;
    private final MoneyProperties moneyProperties;

    @Transactional
    public SplitFareRequest createSplitRequest(CreateSplitFareRequest req) {
        RideDetailsResponse ride = rideServiceClient.getRide(req.rideId())
                .orElseThrow(() -> new NotFoundException("Ride not found: " + req.rideId()));

        if (ride.customerId() != null && !ride.customerId().equals(req.initiatorUserId())
                && !OwnershipGuard.currentPrincipal().isPrivileged()) {
            throw new AccessDeniedException("Access denied: only the ride's customer can split its fare");
        }

        if (ride.totalAmount() == null) {
            throw new PaymentServiceException("Ride " + req.rideId() + " has no fare amount yet");
        }

        int totalPeople = req.members().size() + 1;
        BigDecimal total = ride.totalAmount().value().setScale(2, RoundingMode.HALF_UP);
        String currency = ride.totalAmount().code();
        BigDecimal share = total.divide(BigDecimal.valueOf(totalPeople), 2, RoundingMode.HALF_UP);

        List<SplitParticipant> participants = req.members().stream()
                .map(member -> SplitParticipant.builder()
                        .phoneNumber(PhoneNumbers.toE164(member.phoneNumber()).orElse(member.phoneNumber()))
                        .name(member.name())
                        .shareAmount(share)
                        .shareAmountCurrency(AmountCurrency.of(share, currency))
                        .paymentStatus("PENDING")
                        .build())
                .collect(Collectors.toList());

        SplitFareRequest splitFareRequest = SplitFareRequest.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(TenantAccess.stamp())
                .rideId(req.rideId())
                .initiatorUserId(req.initiatorUserId())
                .totalAmount(total)
                .totalAmountCurrency(AmountCurrency.of(total, currency))
                .participants(participants)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();

        SplitFareRequest saved = splitFareRepository.save(splitFareRequest);
        log.info("Split fare request created with id={} for ride={}", saved.getId(), req.rideId());
        return saved;
    }

    @Transactional
    public SplitFareRequest payParticipant(String splitId, int participantOrder) {
        SplitFareRequest split = splitFareRepository.findById(splitId)
                .filter(found -> TenantAccess.canSee(found.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Split fare request not found: " + splitId));

        if (participantOrder < 0 || participantOrder >= split.getParticipants().size()) {
            throw new PaymentServiceException("No participant at order " + participantOrder);
        }

        SplitParticipant participant = split.getParticipants().get(participantOrder);
        if (!"PAID".equals(participant.getPaymentStatus())) {
            participant.setPaidAt(LocalDateTime.now());
            participant.setPaymentStatus("PAID");
        }

        boolean allPaid = split.getParticipants().stream()
                .allMatch(p -> "PAID".equals(p.getPaymentStatus()));
        split.setStatus(allPaid ? "SETTLED" : "PARTIALLY_PAID");

        SplitFareRequest saved = splitFareRepository.save(split);
        log.info("Payment confirmed for participant order={} in splitId={}, status={}", participantOrder, splitId, saved.getStatus());
        return saved;
    }

    public SplitFareRequest getSplit(String splitId) {
        return splitFareRepository.findById(splitId)
                .filter(found -> TenantAccess.canSee(found.getTenantId()))
                .orElseThrow(() -> new NotFoundException("Split fare request not found: " + splitId));
    }
}
