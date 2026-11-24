package com.heuristiq.gateway.privilege.persistence.jpa;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonMapAttributeConverterTest {

    private final JsonMapAttributeConverter converter = new JsonMapAttributeConverter();

    @Test
    void roundTripsAMap() {
        String json = converter.convertToDatabaseColumn(Map.of("privilegeId", 5));

        assertThat(json).isEqualTo("{\"privilegeId\":5}");
        assertThat(converter.convertToEntityAttribute(json)).containsEntry("privilegeId", 5);
    }

    @Test
    void nullAndBlankAreMappedToNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }

    @Test
    void malformedJsonIsRejected() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("{not json"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("deserialize");
    }

    @Test
    void unserialisableMapIsRejected() {
        Map<String, Object> self = new HashMap<>();
        self.put("me", self);

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(self))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("serialize");
    }
}
