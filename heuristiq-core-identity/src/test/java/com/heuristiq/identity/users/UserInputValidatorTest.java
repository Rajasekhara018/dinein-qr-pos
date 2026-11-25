package com.heuristiq.identity.users;

import com.heuristiq.identity.common.UamException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Email and password rules of {@link UserInputValidator}. */
class UserInputValidatorTest {

    @Test
    void sensibleEmailAddressesPassAndBlankMeansNone() {
        for (String email : List.of("jane@example.com", "jane.doe+tag@sub.example.co.in", "a@b.co", "x_y-z@my-host.example.org",
                "  padded@example.com  ", "a".repeat(64) + "@example.com", "a".repeat(65) + "@example.com", "a@exam-ple.co.uk")) {
            assertThatCode(() -> UserInputValidator.requireValidEmail(email)).as(email).doesNotThrowAnyException();
        }
        assertThatCode(() -> UserInputValidator.requireValidEmail(null)).doesNotThrowAnyException();
        assertThatCode(() -> UserInputValidator.requireValidEmail("  ")).doesNotThrowAnyException();
    }

    @Test
    void malformedEmailAddressesAreRefused() {
        String tooLong = "a@" + ("d".repeat(63) + ".").repeat(3) + "d".repeat(63);
        for (String email : List.of("bad@", "@example.com", "no-at-sign.example.com", "a@b", "a@b.c", "a b@example.com", "a@exam ple.com",
                "a@@example.com", "a@b@example.com", ".@", "a@.com", tooLong)) {
            assertThatThrownBy(() -> UserInputValidator.requireValidEmail(email)).as(email)
                    .isInstanceOf(UamException.class).hasMessageContaining("valid email");
        }
    }

    @Test
    void anAddressOfExactlyTwoHundredFiftyFourCharactersPassesAndOneMoreFails() {
        String atLimit = "a".repeat(254 - "@example.com".length()) + "@example.com";
        assertThat(atLimit).hasSize(254);
        assertThatCode(() -> UserInputValidator.requireValidEmail(atLimit)).doesNotThrowAnyException();
        assertThatThrownBy(() -> UserInputValidator.requireValidEmail("a" + atLimit)).isInstanceOf(UamException.class);
    }

    @Test
    void strongPasswordsPassIncludingTheBoundaries() {
        assertThatCode(() -> UserInputValidator.requireStrongPassword("abcdefg1", 8)).doesNotThrowAnyException();
        assertThatCode(() -> UserInputValidator.requireStrongPassword("Secret#123", 8)).doesNotThrowAnyException();
        assertThatCode(() -> UserInputValidator.requireStrongPassword("a1" + "x".repeat(126), 8)).doesNotThrowAnyException();
        assertThatCode(() -> UserInputValidator.requireStrongPassword("abcdefghij1", 10)).doesNotThrowAnyException();
    }

    @Test
    void shortOrLongPasswordsAreRefused() {
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("a", 8)).isInstanceOf(UamException.class).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("abcdef1", 8)).isInstanceOf(UamException.class).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("abcdefg1", 12)).isInstanceOf(UamException.class).hasMessageContaining("at least 12");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("a1" + "x".repeat(127), 8)).isInstanceOf(UamException.class).hasMessageContaining("at most 128");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword(null, 8)).isInstanceOf(UamException.class);
    }

    @Test
    void aPasswordNeedsALetterAndADigit() {
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("abcdefgh", 8)).isInstanceOf(UamException.class).hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("12345678", 8)).isInstanceOf(UamException.class).hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("!@#$%^&*", 8)).isInstanceOf(UamException.class).hasMessageContaining("letter and one digit");
        // Letters other than A-Z and a-z do not count, so the rule is the same as the forms'.
        assertThatThrownBy(() -> UserInputValidator.requireStrongPassword("пароль123456", 8)).isInstanceOf(UamException.class)
                .hasMessageContaining("letter and one digit");
    }
}
