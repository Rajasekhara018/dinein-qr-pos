package com.heuristiq.notification.domain.model;

import java.util.List;

/** Operator-facing reminders. Deliberately excludes complaint text and requester details from notifications. */
public final class SupportSlaTemplates {
    private SupportSlaTemplates() {}
    public static List<DefaultNotificationTemplates.Seed> seeds() {
        return List.of(
                new DefaultNotificationTemplates.Seed("SUPPORT_TICKET_DUE_SOON", NotificationChannel.IN_APP,
                        "Support deadline approaching - In App", "Ticket deadline approaching",
                        "Ticket {{ticketReference}} must be resolved by {{deadline}}. Open Support tickets to review it."),
                new DefaultNotificationTemplates.Seed("SUPPORT_TICKET_DUE_SOON", NotificationChannel.FCM_PUSH,
                        "Support deadline approaching - Push", "Ticket deadline approaching",
                        "Ticket {{ticketReference}} must be resolved by {{deadline}}. Open Support tickets to review it."),
                new DefaultNotificationTemplates.Seed("SUPPORT_TICKET_OVERDUE", NotificationChannel.IN_APP,
                        "Support ticket overdue - In App", "Ticket resolution overdue",
                        "Ticket {{ticketReference}} passed its resolution deadline {{deadline}}. Please review and escalate it."),
                new DefaultNotificationTemplates.Seed("SUPPORT_TICKET_OVERDUE", NotificationChannel.FCM_PUSH,
                        "Support ticket overdue - Push", "Ticket resolution overdue",
                        "Ticket {{ticketReference}} passed its resolution deadline {{deadline}}. Please review and escalate it."));
    }
}
