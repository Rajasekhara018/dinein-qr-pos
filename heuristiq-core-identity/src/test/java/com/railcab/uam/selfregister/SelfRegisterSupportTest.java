package com.heuristiq.uam.selfregister;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.CommonResponseObject;
import com.heuristiq.uam.common.GlobalExceptionHandler;
import com.heuristiq.uam.common.UamException;

class SelfRegisterSupportTest {

    @Test
    void keysAreNormalizedAndTheAddressIsNeverStoredInTheClear() {
        assertThat(SelfRegisterKeys.normalizeEmail("  Jane@Example.COM ")).isEqualTo("jane@example.com");
        assertThat(SelfRegisterKeys.normalizeEmail(null)).isEmpty();
        assertThat(SelfRegisterKeys.normalizeApplicationCode(" app_x ")).isEqualTo("APP_X");
        assertThat(SelfRegisterKeys.normalizeApplicationCode(null)).isEmpty();
        assertThat(SelfRegisterKeys.domainOf("jane@example.com")).isEqualTo("example.com");
        assertThat(SelfRegisterKeys.domainOf("nodomain")).isEmpty();
        assertThat(SelfRegisterKeys.emailKey("jane@example.com", "APP_X")).isEqualTo("SELFREG:EMAIL:jane@example.com:APP_X");
        assertThat(SelfRegisterKeys.tokenKey("jane@example.com", "APP_X")).isEqualTo("SELFREG:TOKEN:jane@example.com:APP_X");
        assertThat(SelfRegisterKeys.addressKey("203.0.113.5")).startsWith("SELFREG:ADDR:").doesNotContain("203.0.113.5")
                .isEqualTo(SelfRegisterKeys.addressKey("203.0.113.5")).isNotEqualTo(SelfRegisterKeys.addressKey("203.0.113.6"));
        assertThat(SelfRegisterKeys.addressKey(null)).startsWith("SELFREG:ADDR:");
    }

    @Test
    void thePropertiesDefaultToTheSafeValuesAndTheDefaultBotCheckAcceptsEveryone() {
        SelfRegisterProperties defaults = SelfRegisterProperties.defaults();

        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.maxSendsPerIpPerHour()).isEqualTo(20);
        assertThat(defaults.verificationTtlMinutes()).isEqualTo(15);
        assertThat(defaults.blockedEmailDomains()).isEmpty();
        assertThat(defaults.botCheck()).isEqualTo("none");
        assertThat(new NoOpBotCheck().passes(null, "203.0.113.5")).isTrue();
    }

    @Test
    void aFailureCarryingACodeSendsItInTheEnvelopeAndOneWithoutDoesNot() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        CommonResponseObject coded = handler.handleUam(new UamException("nope", HttpStatus.BAD_REQUEST, "OTP_INVALID")).getBody();
        CommonResponseObject plain = handler.handleUam(new UamException("nope")).getBody();

        assertThat(coded.getErrorCode()).isEqualTo("OTP_INVALID");
        assertThat(coded.getMessage()).isEqualTo("nope");
        assertThat(plain.getErrorCode()).isNull();
        assertThat(new UamException("x", HttpStatus.FORBIDDEN).getErrorCode()).isNull();
        assertThat(List.of(coded, plain)).allMatch(response -> !response.getSuccess());
    }
}
