package com.heuristiq.payment.shared.money;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * Reads a {@link Money}: the object {@code {"code": "INR", "value": 150.00}} (the value may be a number or a numeric
 * string), or the legacy shape, a bare number or numeric string, read in the deployment's currency
 * ({@link MoneyDefaults#currency()}). JSON null reads as null. An invalid code, a non-numeric value or more decimals than
 * the currency allows is refused, which the web layer answers with 400.
 */
public class MoneyJsonDeserializer extends StdDeserializer<Money> {

    private static final String CODE = "code";
    private static final String VALUE = "value";

    /** Creates the deserializer (Jackson instantiates it through {@code @JsonDeserialize}). */
    public MoneyJsonDeserializer() {
        super(Money.class);
    }

    @Override
    public Money deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return read(ctxt, MoneyDefaults.currency(), p.getDecimalValue());
        }
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText();
            return text == null || text.isBlank() ? null : read(ctxt, MoneyDefaults.currency(), number(ctxt, text));
        }
        if (token == JsonToken.START_OBJECT) {
            JsonNode node = p.readValueAsTree();
            JsonNode value = node.get(VALUE);
            if (value == null || value.isNull()) {
                return null;
            }
            BigDecimal amount = value.isNumber() ? value.decimalValue() : number(ctxt, value.asText());
            JsonNode code = node.get(CODE);
            return read(ctxt, code == null || code.isNull() ? MoneyDefaults.currency() : code.asText(), amount);
        }
        return (Money) ctxt.handleUnexpectedToken(Money.class, p);
    }

    @Override
    public Money getNullValue(DeserializationContext ctxt) {
        return null;
    }

    private static Money read(DeserializationContext ctxt, String code, BigDecimal value) throws IOException {
        try {
            return Money.parse(code, value);
        } catch (IllegalArgumentException e) {
            throw ctxt.weirdStringException(code + " " + value, Money.class, e.getMessage());
        }
    }

    private static BigDecimal number(DeserializationContext ctxt, String text) throws IOException {
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw ctxt.weirdStringException(text, Money.class, "not a number");
        }
    }
}
