package com.heuristiq.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (security, JPA, Flyway-disabled H2, WebClient,
        // Razorpay config) wires up without errors.
    }
}
