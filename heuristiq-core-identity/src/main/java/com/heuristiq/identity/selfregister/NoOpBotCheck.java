package com.heuristiq.identity.selfregister;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default: no bot check. Active only while {@code railcab.uam.self-register.bot-check} is {@code none}. */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.self-register", name = "bot-check", havingValue = "none", matchIfMissing = true)
class NoOpBotCheck implements SelfRegistrationBotCheck {

    @Override
    public boolean passes(String botToken, String clientAddress) {
        return true;
    }
}
