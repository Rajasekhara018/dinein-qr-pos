package com.heuristiq.paymentservice.shared.money;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

/**
 * An amount with its currency, as every amount travels on the wire: {@code {"code": "INR", "value": 150.00}}.
 * {@code code} is an upper-case ISO 4217 code; {@code value} is a JSON number rounded to the currency's decimal places
 * (INR 2, JPY 0, KWD 3). A missing amount is a null {@code Money}, never a {@code Money} without a value.
 *
 * <p>Reading accepts the legacy shape too (a bare number or numeric string, read in the deployment's currency), see
 * {@link MoneyJsonDeserializer}; writing always produces the object.
 *
 * @param code  ISO 4217 currency code, upper case
 * @param value the amount, scaled to the currency's decimal places
 */
@JsonDeserialize(using = MoneyJsonDeserializer.class)
@Schema(name = "Money", description = "An amount with its currency (ISO 4217 code and a number rounded to the currency's decimals)",
        example = "{\"code\":\"INR\",\"value\":150.00}")
public record Money(
        @Schema(description = "ISO 4217 currency code, upper case", example = "INR") String code,
        @Schema(description = "The amount, rounded to the currency's decimal places", example = "150.00") BigDecimal value) {

    /** Both parts are required: a missing amount is a null {@code Money}, not a {@code Money} without a value. */
    public Money {
        if (code == null || value == null) {
            throw new IllegalArgumentException("Money needs both a currency code and a value");
        }
    }

    /**
     * The amount in {@code code}, rounded half up to the currency's decimal places.
     *
     * @throws IllegalArgumentException when {@code code} is not an ISO 4217 currency or {@code value} is null
     */
    public static Money of(BigDecimal value, String code) {
        if (value == null) {
            throw new IllegalArgumentException("Money needs a value");
        }
        Currency currency = currency(code);
        return new Money(currency.getCurrencyCode(), value.setScale(digits(currency), RoundingMode.HALF_UP));
    }

    /** As {@link #of}, but a null amount gives a null {@code Money}. */
    public static Money ofNullable(BigDecimal value, String code) {
        return value == null ? null : of(value, code);
    }

    /**
     * Strict reading of an amount someone sent: the code must be an ISO 4217 currency and the value may not carry more
     * decimals than the currency allows (a sender's mistake is refused, never silently rounded).
     *
     * @throws IllegalArgumentException when either part is invalid
     */
    public static Money parse(String code, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Money needs a value");
        }
        Currency currency = currency(code);
        int digits = digits(currency);
        if (Math.max(value.stripTrailingZeros().scale(), 0) > digits) {
            throw new IllegalArgumentException("Amount " + value.toPlainString() + " has more than " + digits
                    + " decimal places allowed for " + currency.getCurrencyCode());
        }
        return new Money(currency.getCurrencyCode(), value.setScale(digits, RoundingMode.HALF_UP));
    }

    /** The value of a possibly-null {@code Money}. */
    public static BigDecimal valueOf(Money money) {
        return money == null ? null : money.value();
    }

    /** The code of a possibly-null {@code Money}. */
    public static String codeOf(Money money) {
        return money == null ? null : money.code();
    }

    private static Currency currency(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Money needs a currency code");
        }
        try {
            return Currency.getInstance(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not an ISO 4217 currency code: " + code, e);
        }
    }

    private static int digits(Currency currency) {
        return Math.max(currency.getDefaultFractionDigits(), 0);
    }
}
