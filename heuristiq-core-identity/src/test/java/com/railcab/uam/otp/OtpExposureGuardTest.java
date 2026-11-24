package com.heuristiq.uam.otp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpExposureGuardTest {

    @Test
    void exposeOffIsAlwaysAllowed() {
        assertThatCode(() -> OtpExposureGuard.validate(false, List.of("prod"))).doesNotThrowAnyException();
        assertThatCode(() -> OtpExposureGuard.validate(false, List.of())).doesNotThrowAnyException();
    }

    @Test
    void exposeOnIsAllowedInDevelopmentProfiles() {
        for (String profile : OtpConstants.EXPOSE_OTP_ALLOWED_PROFILES) {
            assertThatCode(() -> OtpExposureGuard.validate(true, List.of(profile))).doesNotThrowAnyException();
        }
        assertThatCode(() -> OtpExposureGuard.validate(true, List.of("local", "postgres"))).doesNotThrowAnyException();
    }

    @Test
    void exposeOnFailsStartupInProductionLikeProfiles() {
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of("prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("railcab.uam.otp.expose-otp");
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of("prod", "vault")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of("uat")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of("dev", "prod")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exposeOnFailsClosedWithNoDevelopmentProfile() {
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> OtpExposureGuard.validate(true, List.of("postgres")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(OtpConstants.EXPOSE_OTP_ALLOWED_PROFILES).containsExactlyInAnyOrder("local", "dev", "test");
    }
}
