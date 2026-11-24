package com.heuristiq.phone;

import com.heuristiq.phone.validation.ValidPhone;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    void theSameIndianNumberWrittenManyWaysIsOneCanonicalValue() {
        for (String written : new String[] {"9876543210", "09876543210", "+91 98765 43210", "+919876543210", "91 9876543210", "(+91) 98765-43210"}) {
            assertThat(PhoneNumbers.toE164(written, "IN")).as(written).contains("+919876543210");
        }
    }

    @Test
    void numbersOfOtherCountriesAreAccepted() {
        assertThat(PhoneNumbers.toE164("+1 415 555 2671")).contains("+14155552671");
        assertThat(PhoneNumbers.toE164("+44 7911 123456")).contains("+447911123456");
        assertThat(PhoneNumbers.toE164("+971 50 123 4567")).contains("+971501234567");
        assertThat(PhoneNumbers.toE164("07911 123456", "GB")).contains("+447911123456");
        PhoneNumbers.Parsed parsed = PhoneNumbers.parse("+971501234567").orElseThrow();
        assertThat(parsed.countryCode()).isEqualTo("971");
        assertThat(parsed.nationalNumber()).isEqualTo("501234567");
        assertThat(parsed.region()).isEqualTo("AE");
    }

    @Test
    void textThatIsNotANumberIsRejected() {
        assertThat(PhoneNumbers.isValid("12345", "IN")).isFalse();
        assertThat(PhoneNumbers.isValid("abcdefghij", "IN")).isFalse();
        assertThat(PhoneNumbers.isValid("", "IN")).isFalse();
        assertThat(PhoneNumbers.isValid(null, "IN")).isFalse();
        assertThat(PhoneNumbers.isValid("0000000000", "IN")).isFalse();
    }

    @Test
    void theRegionDecidesHowANumberWithoutACountryCodeIsRead() {
        assertThat(PhoneNumbers.toE164("4155552671", "US")).contains("+14155552671");
        // The same digits read in another region are a different number (here a valid Indian landline).
        assertThat(PhoneNumbers.toE164("4155552671", "IN")).contains("+914155552671");
    }

    @Test
    void lookupVariantsCoverTheFormsStoredBeforeTheCanonicalOne() {
        assertThat(PhoneNumbers.lookupVariants("98765 43210", "IN")).containsExactly("+919876543210", "919876543210", "9876543210");
        assertThat(PhoneNumbers.lookupVariants("nope", "IN")).isEmpty();
        // A number of another country never yields the bare national digits, which would match a home-region row.
        assertThat(PhoneNumbers.lookupVariants("+1 917 555 0123", "IN")).containsExactly("+19175550123", "19175550123");
        assertThat(PhoneNumbers.lookupVariants("+91 98765 43210", "IN")).contains("9876543210");
    }

    @Test
    void theDefaultRegionCanBeConfigured() {
        String before = System.getProperty(PhoneNumbers.DEFAULT_REGION_PROPERTY);
        try {
            System.setProperty(PhoneNumbers.DEFAULT_REGION_PROPERTY, "us");
            assertThat(PhoneNumbers.defaultRegion()).isEqualTo("US");
            assertThat(PhoneNumbers.toE164("4155552671")).contains("+14155552671");
        } finally {
            if (before == null) {
                System.clearProperty(PhoneNumbers.DEFAULT_REGION_PROPERTY);
            } else {
                System.setProperty(PhoneNumbers.DEFAULT_REGION_PROPERTY, before);
            }
        }
    }

    record Form(@ValidPhone String phone, @ValidPhone(region = "US") String usPhone) {
    }

    @Test
    void theConstraintAcceptsValidBlankAndNullAndRejectsGarbage() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(new Form("9876543210", "4155552671"))).isEmpty();
        assertThat(validator.validate(new Form("+14155552671", "+919876543210"))).isEmpty();
        assertThat(validator.validate(new Form(null, ""))).isEmpty();
        assertThat(validator.validate(new Form("12345", "4155552671"))).hasSize(1);
        assertThat(validator.validate(new Form("9876543210", "9876543210"))).hasSize(1);
    }
}
