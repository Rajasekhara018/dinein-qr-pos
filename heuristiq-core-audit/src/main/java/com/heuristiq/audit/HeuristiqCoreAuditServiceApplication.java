package com.railcab.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the Rider audit service: the central audit trail. It receives access events from the API
 * gateway, delivery and payment-gateway events from NMS and the payment service, and screen views from the
 * Angular and Flutter clients, keeps them per retention class, and serves them to the AdminWeb audit screen.
 * See {@code README.md}.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class HeuristiqCoreAuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(HeuristiqCoreAuditServiceApplication.class, args);
    }
}
