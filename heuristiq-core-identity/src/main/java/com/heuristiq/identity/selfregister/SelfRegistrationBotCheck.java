package com.heuristiq.uam.selfregister;

/**
 * Port for a bot check (a CAPTCHA provider) in front of the sign-up email. The default implementation accepts everything;
 * production should enable a provider before a public launch. A provider checks the {@code botToken} the form posted.
 */
public interface SelfRegistrationBotCheck {

    /**
     * @param botToken      what the form's widget produced, possibly null or blank
     * @param clientAddress the address the request came from
     * @return true when the caller looks like a person
     */
    boolean passes(String botToken, String clientAddress);
}
