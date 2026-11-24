package com.heuristiq.uam.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Validation;

/** A payload that does not fit its type is refused in the caller's own words, never with Jackson's class names. */
class CommonRequestMapperTest {

    record Sample(String username, int age) {
    }

    private final CommonRequestMapper mapper = new CommonRequestMapper(new ObjectMapper(),
            Validation.buildDefaultValidatorFactory().getValidator());

    private static CommonRequestObject request(Map<String, Object> body) {
        CommonRequestObject request = new CommonRequestObject();
        request.setRequestObject(body);
        return request;
    }

    @Test
    void anUnknownFieldIsNamedWithoutJavaClassesOrTheListOfKnownProperties() {
        assertThatThrownBy(() -> mapper.requireRequestObject(request(Map.of("userName", "a")), Sample.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown field: userName")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("com.heuristiq").doesNotContain("known properties"));
    }

    @Test
    void aWronglyTypedValueNamesOnlyItsField() {
        assertThatThrownBy(() -> mapper.requireRequestObject(request(Map.of("username", "a", "age", "old")), Sample.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid value for field: age");
    }

    @Test
    void aMissingBodyIsStillRefused() {
        assertThatThrownBy(() -> mapper.requireRequestObject(null, Sample.class)).hasMessage("requestObject is required");
    }

    @Test
    void aGoodBodyIsConverted() {
        assertThat(mapper.requireRequestObject(request(Map.of("username", "a", "age", 3)), Sample.class)).isEqualTo(new Sample("a", 3));
    }
}
