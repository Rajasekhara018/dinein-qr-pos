package com.heuristiq.identity.selfregister;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Settings of the self-registration email check ({@code railcab.uam.self-register.*}).
 *
 * @param enabled                 false refuses every self-registration call (the kill switch)
 * @param maxSendsPerIpPerHour    most codes one client address may ask for in an hour, whatever the emails: stops one address
 *                                from driving mail to many people
 * @param verificationTtlMinutes  how long the single-use token stays valid after the code was entered
 * @param blockedEmailDomains     domains (for example throw-away mail services) that may not be used; empty by default
 * @param botCheck                {@code none} until a provider is wired; any other value needs a matching
 *                                {@link SelfRegistrationBotCheck} bean or the service refuses to start
 */
@ConfigurationProperties(prefix = "railcab.uam.self-register")
public record SelfRegisterProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") @Min(1) @Max(1000) int maxSendsPerIpPerHour,
        @DefaultValue("15") @Min(1) @Max(120) int verificationTtlMinutes,
        @DefaultValue List<String> blockedEmailDomains,
        @DefaultValue("none") String botCheck) {

    /** The built-in values. */
    public static SelfRegisterProperties defaults() {
        return new SelfRegisterProperties(true, 20, 15, List.of(), "none");
    }
}
