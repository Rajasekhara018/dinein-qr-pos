package com.heuristiq.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * app.settlement.delay-hours — how long a CAPTURED payment sits before the settlement job
 * (SettlementScheduler) bookkeeps it as SETTLED. This is an internal state-machine transition
 * only (see SettlementScheduler's javadoc) — not a call to any external bank-settlement API.
 */
@Component
@ConfigurationProperties(prefix = "app.settlement")
@Getter
@Setter
public class SettlementProperties {
    private int delayHours = 24;
}
