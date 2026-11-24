package com.heuristiq.payment.config;

import com.heuristiq.payment.shared.PaymentCommonConstants;
import com.heuristiq.payment.shared.money.MoneyDefaults;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * The currency this deployment works in ({@code app.payment.currency}, an ISO 4217 code): new payments and top-ups are
 * created in it, the gateway verification expects it, a request amount in another currency is refused, and a legacy
 * bare-number amount on the wire is read in it (published to {@link MoneyDefaults} at startup). Every stored amount
 * carries its own currency (V24).
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.payment")
public class MoneyProperties implements InitializingBean {

    @Pattern(regexp = "[A-Z]{3}")
    private String currency = PaymentCommonConstants.DEFAULT_CURRENCY;

    @Override
    public void afterPropertiesSet() {
        MoneyDefaults.useCurrency(currency);
    }
}
