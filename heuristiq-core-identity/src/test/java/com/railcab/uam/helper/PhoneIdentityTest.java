package com.heuristiq.uam.helper;

import com.heuristiq.uam.common.UamException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How a typed number is stored and when a stored row is the same real number (legacy and international forms). */
class PhoneIdentityTest {

    private static final String LEGACY_NATIONAL = "9876543210";

    private static boolean matches(RPhoneNumber stored, String typed) {
        return PhoneIdentity.matches(stored, typed);
    }

    @Test
    void aLegacyBareRowMatchesTheBareAndTheInternationalInput() {
        RPhoneNumber legacy = new RPhoneNumber(null, LEGACY_NATIONAL);

        assertThat(matches(legacy, "9876543210")).isTrue();
        assertThat(matches(legacy, "+919876543210")).isTrue();
        assertThat(matches(legacy, "+91 98765 43210")).isTrue();
    }

    @Test
    void legacyRowsWithAPlusNinetyOneOrNinetyOneCodeStillMatch() {
        assertThat(matches(new RPhoneNumber("+91", LEGACY_NATIONAL), "+919876543210")).isTrue();
        assertThat(matches(new RPhoneNumber("91", LEGACY_NATIONAL), "9876543210")).isTrue();
    }

    @Test
    void aNewFormRowMatchesBothInputs() {
        RPhoneNumber stored = PhoneIdentity.toStored("+919876543210");

        assertThat(stored.getCountryCode()).isEqualTo("91");
        assertThat(stored.getNumber()).isEqualTo(LEGACY_NATIONAL);
        assertThat(matches(stored, "9876543210")).isTrue();
        assertThat(matches(stored, "+919876543210")).isTrue();
    }

    @Test
    void aNumberOfAnotherCountryNeverMatchesAnIndianRow() {
        assertThat(matches(new RPhoneNumber(null, LEGACY_NATIONAL), "+14155552671")).isFalse();
        assertThat(matches(new RPhoneNumber("91", "4155552671"), "+14155552671")).isFalse();
        assertThat(matches(new RPhoneNumber("91", "9876543210"), "+14155552671")).isFalse();
    }

    @Test
    void aUsRowMatchesOnlyTheUsNumber() {
        RPhoneNumber us = PhoneIdentity.toStored("+14155552671");

        assertThat(us.getCountryCode()).isEqualTo("1");
        assertThat(us.getNumber()).isEqualTo("4155552671");
        assertThat(matches(us, "+1 (415) 555-2671")).isTrue();
        assertThat(matches(us, "4155552671")).isFalse();
    }

    @Test
    void aUnparsableIdentifierMatchesOnlyAnIdenticalLegacyRow() {
        assertThat(matches(new RPhoneNumber(null, "12345"), "12345")).isTrue();
        assertThat(matches(new RPhoneNumber("91", "12345"), "12345")).isFalse();
        assertThat(matches(new RPhoneNumber(null, LEGACY_NATIONAL), "jane.doe")).isFalse();
        assertThat(PhoneIdentity.lookup("  ")).isEmpty();
        assertThat(PhoneIdentity.lookup(null)).isEmpty();
        assertThat(matches(null, LEGACY_NATIONAL)).isFalse();
        assertThat(matches(new RPhoneNumber(), LEGACY_NATIONAL)).isFalse();
    }

    @Test
    void storingAGarbageNumberIsABadRequest() {
        assertThatThrownBy(() -> PhoneIdentity.toStored("not-a-number"))
                .isInstanceOf(UamException.class)
                .hasMessageContaining("valid phone number");
        assertThatThrownBy(() -> PhoneIdentity.toE164("123")).isInstanceOf(UamException.class);
    }

    @Test
    void theBackfillRuleMatchesTheFlywayMigration() {
        assertThat(PhoneIdentity.legacyE164(null, "9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.legacyE164("", "9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.legacyE164("+91", "9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.legacyE164("91", "98765 43210")).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.legacyE164("1", "4155552671")).isEqualTo("+14155552671");
        assertThat(PhoneIdentity.legacyE164(null, "12345")).isNull();
        assertThat(PhoneIdentity.legacyE164("91", "")).isNull();
        assertThat(PhoneIdentity.legacyE164(null, null)).isNull();
    }

    @Test
    void theCanonicalValueIsTheSameForEverySpellingOfARealNumber() {
        String expected = "+919876543210";
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber(null, "9876543210"))).isEqualTo(expected);
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber("+91", "9876543210"))).isEqualTo(expected);
        assertThat(PhoneIdentity.e164Of(PhoneIdentity.toStored("+91 98765 43210"))).isEqualTo(expected);
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber("1", "4155552671"))).isNotEqualTo(
                PhoneIdentity.e164Of(new RPhoneNumber("91", "4155552671")));
    }

    @Test
    void searchTermsWithAPlusOrCountryCodeBecomeAFragment() {
        assertThat(PhoneIdentity.searchFragment("+91 98765 43210")).contains("+919876543210");
        assertThat(PhoneIdentity.searchFragment("+9198")).contains("+9198");
        assertThat(PhoneIdentity.searchFragment("98765")).contains("98765");
        assertThat(PhoneIdentity.searchFragment("jane")).isEmpty();
        assertThat(PhoneIdentity.searchFragment("12")).isEmpty();
        assertThat(PhoneIdentity.searchFragment(null)).isEmpty();
    }

    @Test
    void e164FormsAreCanonicalForDeliveryAndOtpKeys() {
        assertThat(PhoneIdentity.toE164("9876543210")).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.canonical("9876543210")).isEqualTo(PhoneIdentity.canonical("+91 98765 43210"));
        assertThat(PhoneIdentity.canonical(" odd ")).isEqualTo("odd");
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber(null, LEGACY_NATIONAL))).isEqualTo("+919876543210");
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber("1", "4155552671"))).isEqualTo("+14155552671");
        assertThat(PhoneIdentity.e164Of(new RPhoneNumber())).isNull();
        assertThat(PhoneIdentity.e164Of(null)).isNull();
    }
}
