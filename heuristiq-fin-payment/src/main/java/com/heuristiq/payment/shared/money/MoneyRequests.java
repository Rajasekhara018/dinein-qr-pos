package com.heuristiq.payment.shared.money;

import com.heuristiq.payment.shared.exception.PaymentServiceException;
import java.math.BigDecimal;

/** Reads an amount someone sent: it must be in the currency the service works in. */
public final class MoneyRequests {

    private MoneyRequests() {
    }

    /**
     * The value of a request amount in {@code expectedCurrency}.
     *
     * @param amount           the amount sent; may be null (an optional amount)
     * @param expectedCurrency the currency it must be in (the deployment's, or the payment's)
     * @return the value, or null when no amount was sent
     * @throws PaymentServiceException (400) when the amount is in another currency
     */
    public static BigDecimal valueIn(Money amount, String expectedCurrency) {
        if (amount == null) {
            return null;
        }
        if (!amount.code().equalsIgnoreCase(expectedCurrency)) {
            throw new PaymentServiceException("Amount currency " + amount.code() + " is not accepted; amounts here are in "
                    + expectedCurrency);
        }
        return amount.value();
    }
}
