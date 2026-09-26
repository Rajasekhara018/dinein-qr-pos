package com.heuristq.dinein.notification.email;

/** Plain-text email; the sender address comes from {@code app.notifications.email.from}. */
public record EmailMessage(String to, String subject, String body) {
}
