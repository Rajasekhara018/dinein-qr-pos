package com.heuristiq.payment.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.heuristiq.payment.payment.dto.PaymentResponse;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.service.DirectCollectionService;
import com.heuristiq.payment.shared.exception.ConflictException;
import com.heuristiq.payment.shared.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalDirectCollectionControllerTest {

    private static final String PATH = "/api/v1/internal/payments/direct-collections";
    private static final String BODY = "{\"rideId\":\"ride-1\",\"customerId\":\"cust-1\",\"driverId\":\"drv-1\","
            + "\"amount\":{\"code\":\"INR\",\"value\":150.00},\"channel\":\"UPI\"}";

    private DirectCollectionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(DirectCollectionService.class);
        mvc = MockMvcBuilders.standaloneSetup(new InternalDirectCollectionController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static PaymentResponse payment() {
        return PaymentResponse.from(Payment.builder().internalPaymentId("p-1").rideId("ride-1").currency("INR")
                .amount(new BigDecimal("150.00")).method(PaymentMethod.UPI_DIRECT).status(PaymentStatus.CAPTURED).build());
    }

    @Test
    void recordedNowIs201_aRepeatIs200() throws Exception {
        when(service.record(any())).thenReturn(new DirectCollectionService.Recorded(payment(), false),
                new DirectCollectionService.Recorded(payment(), true));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.object.method").value("UPI_DIRECT"))
                .andExpect(jsonPath("$.object.status").value("CAPTURED"));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidBodyIs400AndRecordsNothing() throws Exception {
        String[] bodies = {
                BODY.replace("\"channel\":\"UPI\"", "\"channel\":\"CARD\""),
                BODY.replace("\"rideId\":\"ride-1\",", ""),
                BODY.replace("\"driverId\":\"drv-1\",", ""),
                BODY.replace("150.00", "0"),
        };
        for (String body : bodies) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verify(service, never()).record(any());
    }

    @Test
    void anotherOrganizationsRideIs409() throws Exception {
        when(service.record(any())).thenThrow(new ConflictException("other organization"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isConflict());
    }
}
