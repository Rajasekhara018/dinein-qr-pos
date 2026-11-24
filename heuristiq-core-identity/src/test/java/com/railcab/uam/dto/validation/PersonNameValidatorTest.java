package com.heuristiq.uam.dto.validation;

import com.heuristiq.uam.dto.IdentityRegisterRequest;
import com.heuristiq.uam.enums.UamEnums;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The self-registration name rule: letters of any script, spaces, '.', apostrophes, hyphens; 2..80 trimmed. */
class PersonNameValidatorTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<ConstraintViolation<IdentityRegisterRequest>> validate(String name) {
        return validator.validate(new IdentityRegisterRequest(name, "9876543210", "a@b.com", null, UamEnums.UserType.CUSTOMER));
    }

    @Test
    void acceptsIndianScriptsWithCombiningMarksAndPunctuation() {
        for (String name : new String[] {"राम कुमार", "முருகன்", "రామ్", "ಕುಮಾರ್", "മുരളി", "محمد علي",
                "O'Brien", "O’Brien", "Jean-Luc", "A. Kumar", "Jo"}) {
            assertThat(validate(name)).as(name).isEmpty();
        }
    }

    @Test
    void rejectsDigitsSymbolsAndANonLetterStart() {
        for (String name : new String[] {"Ravi1", "123", "Ravi_K", "Ravi@", "-Ravi", "'Ravi", " .Ravi"}) {
            Set<ConstraintViolation<IdentityRegisterRequest>> violations = validate(name);
            assertThat(violations).as(name).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).isEqualTo(PersonNameConstants.INVALID_CHARACTERS_MESSAGE);
        }
    }

    @Test
    void enforcesTheTrimmedLength() {
        assertThat(validate(" A ").iterator().next().getMessage()).isEqualTo(PersonNameConstants.INVALID_LENGTH_MESSAGE);
        assertThat(validate("a".repeat(80))).isEmpty();
        assertThat(validate("  " + "a".repeat(80) + "  ")).isEmpty();
        assertThat(validate("a".repeat(81)).iterator().next().getMessage()).isEqualTo(PersonNameConstants.INVALID_LENGTH_MESSAGE);
    }

    @Test
    void blankIsReportedOnlyByNotBlank() {
        Set<ConstraintViolation<IdentityRegisterRequest>> violations = validate("  ");
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isNotEqualTo(PersonNameConstants.INVALID_CHARACTERS_MESSAGE);
        assertThat(validate(null)).hasSize(1);
    }
}
