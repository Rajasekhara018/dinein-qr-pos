package com.heuristiq.uam.dto;

import com.heuristiq.uam.enums.UamEnums;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Phone fields accept a valid number of any country and reject garbage, instead of only Indian 10-digit mobiles. */
class PhoneRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean otpAccepts(String phone) {
        return validator.validate(new IdentityPhoneOtpRequest(phone, UamEnums.UserType.CUSTOMER)).isEmpty();
    }

    private boolean userAccepts(String phone) {
        return validator.validate(new UpsertUserRequest("jane", "pw", "Jane", null, "jane@example.com", phone, null, null)).isEmpty();
    }

    @Test
    void indianUsAndInternationalNumbersAreAccepted() {
        for (String phone : new String[] {"9876543210", "+919876543210", "+14155552671", "+44 20 7946 0958"}) {
            assertThat(otpAccepts(phone)).as(phone).isTrue();
            assertThat(userAccepts(phone)).as(phone).isTrue();
        }
    }

    @Test
    void garbageIsRejectedWithTheNewMessage() {
        var violations = validator.validate(new IdentityPhoneOtpRequest("abc123", UamEnums.UserType.CUSTOMER));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must be a valid phone number");
        assertThat(userAccepts("12345")).isFalse();
    }

    @Test
    void aRequiredPhoneStillCannotBeBlankButAnOptionalOneCan() {
        assertThat(otpAccepts("")).isFalse();
        assertThat(userAccepts(null)).isTrue();
        assertThat(userAccepts("")).isTrue();
    }

    @Test
    void registrationAcceptsAnInternationalMobile() {
        var request = new IdentityRegisterRequest("Jane", "+14155552671", "jane@example.com", null, UamEnums.UserType.CUSTOMER);
        assertThat(validator.validate(request)).isEmpty();
        assertThat(validator.validate(new IdentityRegisterRequest("Jane", "xyz", "jane@example.com", null, UamEnums.UserType.CUSTOMER))).hasSize(1);
    }
}
