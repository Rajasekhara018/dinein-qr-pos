package com.heuristiq.paymentservice.payment.gateway.pinelabs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.heuristiq.paymentservice.payment.model.Payment;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PineLabsCustomerDetailsTest {

    private static Map<String, Object> detailsFor(String mobile) {
        Payment payment = Payment.builder().customerId("cust-1").customerMobile(mobile).build();
        return PineLabsCreateSessionGatewayClient.customerDetails(payment);
    }

    @Test
    void indianNumberGivesCountryCode91AndTheNationalNumber() {
        for (String mobile : new String[] {"+919876543210", "9876543210"}) {
            Map<String, Object> customer = detailsFor(mobile);
            assertEquals("91", customer.get("country_code"));
            assertEquals("9876543210", customer.get("mobile_number"));
        }
    }

    @Test
    void otherCountriesUseTheirOwnCallingCode() {
        Map<String, Object> uae = detailsFor("+971501234567");
        assertEquals("971", uae.get("country_code"));
        assertEquals("501234567", uae.get("mobile_number"));
        Map<String, Object> us = detailsFor("+14155552671");
        assertEquals("1", us.get("country_code"));
        assertEquals("4155552671", us.get("mobile_number"));
    }

    @Test
    void noValidNumberSendsNoCountryCode() {
        for (String mobile : new String[] {null, "", "garbage", "123"}) {
            Map<String, Object> customer = detailsFor(mobile);
            assertEquals("cust-1", customer.get("customer_id"));
            assertFalse(customer.containsKey("country_code"));
            assertFalse(customer.containsKey("mobile_number"));
        }
    }
}
