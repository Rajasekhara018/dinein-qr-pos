package com.heuristiq.identity.application;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The size limits that stop an over-long value reaching a varchar column (which would fail the save with a 500). */
class UpsertApplicationRequestTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void aDescriptionOfUpToAThousandCharactersIsAccepted() {
        assertThat(validator.validate(request("d".repeat(1000), "PASUPU_KUMKUMA_MATRIMONY_APP"))).isEmpty();
    }

    @Test
    void aLongerDescriptionIsRejectedWithAClearMessage() {
        var violations = validator.validate(request("d".repeat(1001), "PASUPU_KUMKUMA_MATRIMONY_APP"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("description");
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must be at most 1000 characters");
    }

    @Test
    void otherTextFieldsAreHeldToTheirColumnWidth() {
        assertThat(validator.validate(request(null, "C".repeat(101)))).hasSize(1);
    }

    private UpsertApplicationRequest request(String description, String code) {
        return new UpsertApplicationRequest(code, "Name", description, null, null, null, null, null, null, null, null, null, null);
    }
}
