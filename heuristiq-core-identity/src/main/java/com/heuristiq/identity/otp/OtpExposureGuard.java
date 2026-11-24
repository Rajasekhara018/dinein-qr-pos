package com.heuristiq.uam.otp;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;

/**
 * Refuses to start when {@code railcab.uam.otp.expose-otp} is true (it returns the OTP in the send
 * response, which defeats the second factor) outside a development profile. Allowed profiles are
 * {@link OtpConstants#EXPOSE_OTP_ALLOWED_PROFILES}; the additive {@link OtpConstants#NEUTRAL_PROFILES}
 * (postgres, vault) are ignored, any other active profile -- or none at all -- is treated as production-like.
 */
@Component
public class OtpExposureGuard {
    private static final Logger log = LoggerFactory.getLogger(OtpExposureGuard.class);

    private final boolean exposeOtp;
    private final Environment environment;

    public OtpExposureGuard(@Value("${railcab.uam.otp.expose-otp:false}") boolean exposeOtp, Environment environment) {
        this.exposeOtp = exposeOtp;
        this.environment = environment;
    }

    /** Fails startup with a clear message, or logs the warning, per the rules above. */
    @PostConstruct
    void check() {
        validate(exposeOtp, Arrays.asList(environment.getActiveProfiles()));
    }

    /**
     * @throws IllegalStateException if {@code exposeOtp} is true and {@code activeProfiles} are not all development ones
     */
    static void validate(boolean exposeOtp, Collection<String> activeProfiles) {
        if (!exposeOtp) {
            return;
        }
        boolean hasDevProfile = activeProfiles.stream().anyMatch(OtpConstants.EXPOSE_OTP_ALLOWED_PROFILES::contains);
        boolean hasOtherProfile = activeProfiles.stream()
                .anyMatch(p -> !OtpConstants.EXPOSE_OTP_ALLOWED_PROFILES.contains(p) && !OtpConstants.NEUTRAL_PROFILES.contains(p));
        if (!hasDevProfile || hasOtherProfile) {
            throw new IllegalStateException("railcab.uam.otp.expose-otp must be false unless the active profiles are limited to "
                    + OtpConstants.EXPOSE_OTP_ALLOWED_PROFILES + " (active: " + activeProfiles + "). Refusing to start.");
        }
        log.warn("SECURITY WARNING: railcab.uam.otp.expose-otp is TRUE -- OTPs are returned in API responses. "
                + "Never enable this outside local development.");
    }
}
