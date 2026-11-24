package com.heuristiq.paymentservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.paymentservice.client.dto.RideDetailsResponse;
import com.heuristiq.paymentservice.events.DurableEventOutbox;
import com.heuristiq.paymentservice.payment.dto.PaymentResponse;
import com.heuristiq.paymentservice.payment.dto.RefundRequest;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import com.heuristiq.paymentservice.shared.money.Money;
import com.heuristiq.paymentservice.shared.money.MoneyRequests;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.client.WebClient;

/** Every amount on Payment's wire is {code, value}; what other services send in the old bare-number shape still reads. */
class PaymentWireMoneyTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void thePaymentResultEventCarriesFinalAmountAsCodeAndValue() throws Exception {
        DurableEventOutbox outbox = mock(DurableEventOutbox.class);
        RideServiceClient client = new RideServiceClient(WebClient.builder().exchangeFunction(r -> reactor.core.publisher.Mono.empty()).build(),
                new ResilientCaller("ride-test", com.heuristiq.resilient.ResilienceProfile.defaults()), outbox, mapper);
        // outside a transaction the direct call is attempted too; it fails quietly and the outbox still has the event
        client.notifyPaymentResult("ride-1", "COMPLETED", "pay-1", Money.of(new BigDecimal("150"), "INR"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outbox).enqueue(anyString(), eq("ride-payment-results"), eq("ride-1"), payload.capture());
        JsonNode event = mapper.readTree(payload.getValue());
        assertThat(event.path("finalAmount").path("code").asText()).isEqualTo("INR");
        assertThat(event.path("finalAmount").path("value").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(event.path("finalAmount").path("value").isNumber()).isTrue();
    }

    @Test
    void theRideFareReadsInTheNewAndTheLegacyShape() throws Exception {
        RideDetailsResponse current = mapper.readValue("""
                {"id":"r1","customerId":"c1","driverId":"d1","estimatedFare":{"code":"INR","value":140.5},
                 "finalFare":{"code":"INR","value":150},"status":"COMPLETED"}""", RideDetailsResponse.class);
        assertThat(current.totalAmount()).isEqualTo(Money.of(new BigDecimal("150"), "INR"));

        RideDetailsResponse legacy = mapper.readValue("""
                {"id":"r1","estimatedFare":140.5,"finalFare":null,"status":"COMPLETED"}""", RideDetailsResponse.class);
        assertThat(legacy.totalAmount()).isEqualTo(Money.of(new BigDecimal("140.50"), "INR"));
    }

    @Test
    void aPaymentResponseCarriesTheAmountAsCodeAndValueAndNoSeparateCurrency() throws Exception {
        Payment p = Payment.builder().internalPaymentId("pay-1").rideId("r1").customerId("c1")
                .amount(new BigDecimal("199.5")).currency("INR").status(PaymentStatus.CAPTURED).method(PaymentMethod.CASH).build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(PaymentResponse.from(p)));

        assertThat(json.path("amount").path("code").asText()).isEqualTo("INR");
        assertThat(json.path("amount").path("value").decimalValue()).isEqualByComparingTo("199.50");
        assertThat(json.has("currency")).isFalse();
    }

    @Test
    void aRefundRequestReadsTheNewAndTheLegacyShape() throws Exception {
        assertThat(mapper.readValue("{\"amount\":{\"code\":\"INR\",\"value\":40}}", RefundRequest.class).amount())
                .isEqualTo(Money.of(new BigDecimal("40"), "INR"));
        assertThat(mapper.readValue("{\"amount\":40}", RefundRequest.class).amount())
                .isEqualTo(Money.of(new BigDecimal("40"), "INR"));
        assertThat(mapper.readValue("{\"reason\":\"x\"}", RefundRequest.class).amount()).isNull();
    }

    @Test
    void aRequestAmountInAnotherCurrencyIsRefused() {
        assertThat(MoneyRequests.valueIn(Money.of(new BigDecimal("5"), "INR"), "inr")).isEqualByComparingTo("5.00");
        assertThat(MoneyRequests.valueIn(null, "INR")).isNull();
        assertThatThrownBy(() -> MoneyRequests.valueIn(Money.of(new BigDecimal("5"), "AED"), "INR"))
                .isInstanceOf(PaymentServiceException.class).hasMessageContaining("AED");
    }
}
