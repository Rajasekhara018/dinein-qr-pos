package com.heuristiq.uam.readiness.application.port;

/**
 * Someone an alert may be sent to.
 *
 * @param userId   account id
 * @param name     name to greet
 * @param email    address (an alert is only sent to people who have one)
 * @param language two-letter language code of the account ({@code en}, {@code hi}, ...), or null for the default
 */
public record AlertRecipient(String userId, String name, String email, String language) {
}
