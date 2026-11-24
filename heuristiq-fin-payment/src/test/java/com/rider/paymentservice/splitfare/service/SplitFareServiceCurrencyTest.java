package com.heuristiq.paymentservice.splitfare.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.client.dto.RideDetailsResponse;
import com.heuristiq.paymentservice.config.MoneyProperties;
import com.heuristiq.paymentservice.splitfare.dto.CreateSplitFareRequest;
import com.heuristiq.paymentservice.splitfare.dto.SplitMemberDto;
import com.heuristiq.paymentservice.splitfare.model.SplitFareRequest;
import com.heuristiq.paymentservice.splitfare.repository.SplitFareRequestRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The split total and every share are exact two-decimal amounts recorded in the deployment's currency. */
class SplitFareServiceCurrencyTest {

    @Test
    void theTotalAndEveryShareCarryTheConfiguredCurrency() {
        SplitFareRequestRepository repository = mock(SplitFareRequestRepository.class);
        RideServiceClient rides = mock(RideServiceClient.class);
        when(repository.save(any(SplitFareRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rides.getRide("ride-1")).thenReturn(Optional.of(
                new RideDetailsResponse("ride-1", "cust-1", "driver-1", com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("99.99"), "AED"), com.heuristiq.paymentservice.shared.money.Money.of(new BigDecimal("100"), "AED"), "COMPLETED")));
        MoneyProperties money = new MoneyProperties();
        money.setCurrency("AED");
        SplitFareService service = new SplitFareService(repository, rides, money);

        SplitFareRequest split = service.createSplitRequest(new CreateSplitFareRequest("ride-1", "cust-1",
                List.of(new SplitMemberDto("+919876543210", "A"), new SplitMemberDto("+919876543211", "B"))));

        // The final fare wins over the estimate; 100.00 / 3 people = 33.33 each.
        assertThat(split.getTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(split.getTotalAmountCurrency()).isEqualTo("AED");
        assertThat(split.getParticipants()).hasSize(2).allSatisfy(p -> {
            assertThat(p.getShareAmount()).isEqualByComparingTo("33.33");
            assertThat(p.getShareAmountCurrency()).isEqualTo("AED");
        });
    }

    @Test
    void theResponseShapeIsUnchanged_theCurrenciesAreNotSerializedYet() throws Exception {
        SplitFareRequest split = SplitFareRequest.builder().id("s1").totalAmount(new BigDecimal("10.50")).totalAmountCurrency("INR")
                .participants(List.of(com.heuristiq.paymentservice.splitfare.model.SplitParticipant.builder()
                        .shareAmount(new BigDecimal("5.25")).shareAmountCurrency("INR").build()))
                .build();

        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(split);

        assertThat(json).contains("\"totalAmount\":10.50", "\"shareAmount\":5.25").doesNotContain("Currency");
    }
}
