package com.heuristiq.payment.splitfare.dto;

import com.heuristiq.payment.shared.money.Money;
import com.heuristiq.payment.splitfare.model.SplitFareRequest;
import com.heuristiq.payment.splitfare.model.SplitParticipant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * A split-fare request as the API returns it. Same field names as the entity used to serialise to, with every amount as
 * {@code {code, value}}.
 */
public record SplitFareResponse(
        String id,
        String rideId,
        String initiatorUserId,
        String tenantId,
        Money totalAmount,
        List<Participant> participants,
        String status,
        LocalDateTime createdAt
) {

    /** One person's share of the fare. */
    public record Participant(String phoneNumber, String name, Money shareAmount, String paymentStatus, LocalDateTime paidAt) {

        static Participant from(SplitParticipant p) {
            return new Participant(p.getPhoneNumber(), p.getName(), Money.ofNullable(p.getShareAmount(), p.getShareAmountCurrency()),
                    p.getPaymentStatus(), p.getPaidAt());
        }
    }

    /** Maps the stored request (amount + its currency column) to the response. */
    public static SplitFareResponse from(SplitFareRequest s) {
        return new SplitFareResponse(s.getId(), s.getRideId(), s.getInitiatorUserId(), s.getTenantId(),
                Money.ofNullable(s.getTotalAmount(), s.getTotalAmountCurrency()),
                s.getParticipants() == null ? List.of() : s.getParticipants().stream().map(Participant::from).toList(),
                s.getStatus(), s.getCreatedAt());
    }
}
