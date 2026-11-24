package com.heuristiq.paymentservice.salesettlement.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wiring only: binds {@code rider.sale-settlement.*}. */
@Configuration
@EnableConfigurationProperties(SaleSettlementProperties.class)
public class SaleSettlementConfiguration {
}
